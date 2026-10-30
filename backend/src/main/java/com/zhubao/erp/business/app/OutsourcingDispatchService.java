package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 确认所发份额并把保管从本厂转到外协方。部分发外先分袋，来源和权属不变。
 * 不生成客户出货、应收或员工产量。在外数量由发外明细汇总，不另设可手改余额。
 * 未知重量保持未知，不按颗数分摊，也不写成 0。
 */
public class OutsourcingDispatchService {

    public static final String IN_HOUSE = "IN_HOUSE";

    private final OutsourcingSourceRepository sources;
    private final OutsourcingDispatchRepository dispatches;
    private final ActivityBagRepository bags;
    private final BagSplitService splits;
    private final BusinessWorkUnit workUnit;

    public OutsourcingDispatchService(OutsourcingSourceRepository sources, OutsourcingDispatchRepository dispatches,
                                      ActivityBagRepository bags, BagSplitService splits, BusinessWorkUnit workUnit) {
        this.sources = sources;
        this.dispatches = dispatches;
        this.bags = bags;
        this.splits = splits;
        this.workUnit = workUnit;
    }

    public OutsourcingDispatch dispatch(String requestId, String actorId, String taskId, String bagId,
                                        int bagVersion, BigDecimal quantity) {
        String actor = actorId == null ? "" : actorId.trim();
        if (actor.isEmpty()) {
            throw new BusinessException("操作人不能为空");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("发外数量必须为正");
        }
        String task = taskId == null ? "" : taskId.trim();
        String bag = bagId == null ? "" : bagId.trim();
        String payload = task + "|" + bag + "|" + bagVersion + "|" + quantity.stripTrailingZeros().toPlainString();
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "OUTSOURCE_DISPATCH", "OUTSOURCE_TASK", task, payload
        ), session -> send(requestId, actor, task, bag, bagVersion, quantity, session));
        return dispatches.find(result).orElseThrow(() -> new IllegalStateException("外协发外保存后无法读取"));
    }

    /** 该任务仍在外的数量。只读发外明细，不写库存。 */
    public BigDecimal entrustedQuantity(String taskId) {
        return dispatches.listByTask(taskId).stream()
                .map(OutsourcingDispatch::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 袋当前保管方。没有发外记录时仍在本厂。 */
    public String holderOf(String bagId) {
        return dispatches.findBySentBag(bagId).map(OutsourcingDispatch::partyId).orElse(IN_HOUSE);
    }

    private String send(String requestId, String actor, String taskId, String bagId, int bagVersion,
                        BigDecimal quantity, WorkSession session) {
        ActivityBag bag = bags.lock(bagId, session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (bag.version() != bagVersion) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        if (!bag.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能发外");
        }
        OutsourcingTask task = sources.findTask(taskId).orElseThrow(() -> new BusinessException("外协任务不存在"));
        if (actor.equals(task.partyId())) {
            throw new BusinessException("经办人不是外协方");
        }
        StockMeasurement.rejectUndecidedGoldScale(task.businessKind());
        if (!belongsToTask(bag, task)) {
            throw new BusinessException("所发袋不属于该外协任务");
        }
        if (quantity.compareTo(bag.quantity()) > 0) {
            throw new BusinessException("不能超发");
        }
        if (dispatches.findBySentBag(bag.id()).isPresent()) {
            throw new BusinessException("该袋已发外");
        }
        OwnerType owner = StockMeasurement.ownerAfterOutsourcing(task.ownerType());
        String sentBagId = bag.id();
        BigDecimal sentQuantity = bag.quantity();
        if (quantity.compareTo(bag.quantity()) < 0) {
            List<String> children = splits.splitWithin(bag.id(), List.of(quantity, bag.quantity().subtract(quantity)), session);
            sentBagId = children.get(0);
            sentQuantity = quantity;
        }
        String dispatchId = "OD" + UUID.randomUUID().toString().replace("-", "");
        OutsourcingDispatch dispatch = new OutsourcingDispatch(
                dispatchId, requestId, task.taskId(), task.partyId(), bag.id(), sentBagId,
                sentQuantity, bag.sourceKey(), owner, task.ownerRef(), 1);
        dispatches.save(dispatch, session);
        return dispatch.dispatchId();
    }

    private boolean belongsToTask(ActivityBag bag, OutsourcingTask task) {
        if (!bag.targetCode().equals(task.targetCode())) {
            return false;
        }
        String cursor = bag.id();
        while (cursor != null && !cursor.isBlank()) {
            if (cursor.equals(task.bagId())) {
                return true;
            }
            ActivityBag current = bags.find(cursor).orElse(null);
            if (current == null || current.parentId() == null || current.parentId().isBlank()) {
                return false;
            }
            cursor = current.parentId();
        }
        return false;
    }
}
