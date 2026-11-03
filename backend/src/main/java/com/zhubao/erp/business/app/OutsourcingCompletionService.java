package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.NodePortionWriter;
import com.zhubao.erp.business.domain.OutsourcingCompletion;
import com.zhubao.erp.business.domain.OutsourcingCompletionRepository;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.PortionReleaseRepository;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.ProcessStep;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasis;
import com.zhubao.erp.finance.domain.OutsourcingFeeBasisRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;

/**
 * 把实收份额接回原节点，并按这笔任务写出费用依据。金额可以不填，不生成应付。
 * 不放行质量，不把未加工退回记成完工，也不写客户账或员工工资。同一收回只确认一次。
 */
@org.springframework.stereotype.Service
public class OutsourcingCompletionService {

    private final OutsourcingReturnRepository returns;
    private final OutsourcingDispatchRepository dispatches;
    private final OutsourcingSourceRepository sources;
    private final OutsourcingCompletionRepository completions;
    private final OutsourcingFeeBasisRepository fees;
    private final ActivityBagRepository bags;
    private final RouteSnapshotRepository routes;
    private final NodePortionReader portionReader;
    private final NodePortionWriter portionWriter;
    private final PortionReleaseRepository releases;
    private final BusinessWorkUnit workUnit;

    public OutsourcingCompletionService(OutsourcingReturnRepository returns, OutsourcingDispatchRepository dispatches,
                                        OutsourcingSourceRepository sources, OutsourcingCompletionRepository completions,
                                        OutsourcingFeeBasisRepository fees, ActivityBagRepository bags,
                                        RouteSnapshotRepository routes, NodePortionReader portionReader,
                                        NodePortionWriter portionWriter, PortionReleaseRepository releases,
                                        BusinessWorkUnit workUnit) {
        this.returns = returns;
        this.dispatches = dispatches;
        this.sources = sources;
        this.completions = completions;
        this.fees = fees;
        this.bags = bags;
        this.routes = routes;
        this.portionReader = portionReader;
        this.portionWriter = portionWriter;
        this.releases = releases;
        this.workUnit = workUnit;
    }

    public java.util.Optional<OutsourcingCompletion> findByReturn(String returnId) { return completions.findByReturn(returnId); }

    public OutsourcingCompletion confirm(String requestId, String actorId, String returnId) {
        return confirm(requestId, actorId, returnId, null);
    }

    public OutsourcingCompletion confirm(String requestId, String actorId, String returnId, BigDecimal amount) {
        String actor = actorId == null ? "" : actorId.trim();
        if (actor.isEmpty()) {
            throw new BusinessException("操作人不能为空");
        }
        if (amount != null && amount.signum() == 0) {
            throw new BusinessException("未知金额不能写成 0");
        }
        String id = returnId == null ? "" : returnId.trim();
        String amountText = amount == null ? "" : amount.toPlainString();
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "OUTSOURCE_COMPLETE", "OUTSOURCE_RETURN", id, id + "|" + amountText
        ), session -> confirmIn(requestId, actor, id, amount, session));
        return completions.find(result).orElseThrow(() -> new IllegalStateException("外协完成量保存后无法读取"));
    }

    private String confirmIn(String requestId, String actor, String returnId, BigDecimal optionalAmount, WorkSession session) {
        OutsourcingReturn returned = returns.find(returnId).orElseThrow(() -> new BusinessException("外协收回不存在"));
        if (returned.receivedQty().signum() <= 0 || returned.receivedBagId().isBlank()) {
            throw new BusinessException("未加工退回不能记成外协完工");
        }
        if (actor.equals(returned.partyId())) {
            throw new BusinessException("经办人不是外协方");
        }
        OutsourcingDispatch dispatch = dispatches.find(returned.dispatchId())
                .orElseThrow(() -> new BusinessException("外协发外不存在"));
        OutsourcingTask task = sources.findTask(dispatch.taskId()).orElseThrow(() -> new BusinessException("外协任务不存在"));
        StockMeasurement.rejectUndecidedGoldScale(task.businessKind());
        ProcessStep step = ProcessCatalog.require(task.nodeId());
        if (step.kind() != task.businessKind()) {
            throw new BusinessException("工序不属于该业务");
        }
        RouteSnapshot route = routes.list(task.businessKind(), task.targetCode()).stream()
                .filter(RouteSnapshot::confirmed)
                .max(Comparator.comparingInt(RouteSnapshot::version))
                .orElseThrow(() -> new BusinessException("原目标还没有已确认路线"));
        if (!route.stepIds().contains(step.id())) {
            throw new BusinessException("外协节点不在已确认路线上");
        }
        ActivityBag bag = bags.lock(returned.receivedBagId(), session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (!bag.activeLeaf() || !bag.targetCode().equals(task.targetCode())) {
            throw new BusinessException("所发袋不属于该外协任务");
        }
        if (completions.findByReturn(returned.returnId()).isPresent()) {
            throw new BusinessException("外协完成量已记录");
        }
        portionReader.find(bag.id(), step.id(), session).ifPresent(current -> {
            if (current.handled().signum() > 0) {
                throw new BusinessException("外协完成量不能与内部报工重复");
            }
        });
        PortionRelease before = releases.find(bag.id()).orElse(new PortionRelease(bag.id(), BigDecimal.ZERO, BigDecimal.ZERO));
        portionWriter.save(new NodePortionBalance(bag.id(), step.id(), returned.receivedQty(), returned.receivedQty()), session);
        int index = route.stepIds().indexOf(step.id());
        if (index >= 0 && index + 1 < route.stepIds().size()) {
            String nextStep = route.stepIds().get(index + 1);
            NodePortionBalance next = portionReader.find(bag.id(), nextStep, session)
                    .orElse(new NodePortionBalance(bag.id(), nextStep, BigDecimal.ZERO, BigDecimal.ZERO));
            portionWriter.save(new NodePortionBalance(
                    bag.id(), nextStep, next.arrived().add(returned.receivedQty()), next.handled()), session);
        }
        String completionId = "OC" + UUID.randomUUID().toString().replace("-", "");
        OutsourcingCompletion completion = new OutsourcingCompletion(
                completionId, requestId, returned.returnId(), task.taskId(), returned.dispatchId(),
                bag.id(), step.id(), task.targetCode(), returned.receivedQty());
        completions.save(completion, session);
        fees.save(new OutsourcingFeeBasis(
                "FB" + UUID.randomUUID().toString().replace("-", ""), completion.completionId(), task.taskId(),
                returned.partyId(), task.targetCode(), step.id(), returned.receivedQty(), optionalAmount), session);
        PortionRelease after = releases.find(bag.id()).orElse(new PortionRelease(bag.id(), BigDecimal.ZERO, BigDecimal.ZERO));
        if (after.released().compareTo(releasedOf(before)) != 0 || after.frozen().compareTo(frozenOf(before)) != 0) {
            throw new BusinessException("收回不能自动质量放行");
        }
        return completion.completionId();
    }

    private static BigDecimal releasedOf(PortionRelease release) {
        return release.released() == null ? BigDecimal.ZERO : release.released();
    }

    private static BigDecimal frozenOf(PortionRelease release) {
        return release.frozen() == null ? BigDecimal.ZERO : release.frozen();
    }
}
