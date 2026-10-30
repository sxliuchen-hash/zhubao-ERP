package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingReturn;
import com.zhubao.erp.business.domain.OutsourcingReturnBalance;
import com.zhubao.erp.business.domain.OutsourcingReturnRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 按原发外行分批收回。少回留下仍在外的数量，不把差额自动记成损耗，也不结清。
 * 未加工退回只回到本厂，不写工序完成、质检放行、客户出货或工资。
 * 实重单独保存；未知重量不是 0，也不按颗数分摊到子袋。
 */
@Service
public class OutsourcingReturnService {

    private final OutsourcingDispatchRepository dispatches;
    private final OutsourcingReturnRepository returns;
    private final OutsourcingSourceRepository sources;
    private final ActivityBagRepository bags;
    private final BagSplitService splits;
    private final BusinessWorkUnit workUnit;

    public OutsourcingReturnService(OutsourcingDispatchRepository dispatches, OutsourcingReturnRepository returns,
                                    OutsourcingSourceRepository sources, ActivityBagRepository bags,
                                    BagSplitService splits, BusinessWorkUnit workUnit) {
        this.dispatches = dispatches;
        this.returns = returns;
        this.sources = sources;
        this.bags = bags;
        this.splits = splits;
        this.workUnit = workUnit;
    }

    public OutsourcingReturn receive(String requestId, String actorId, String dispatchId, int dispatchVersion,
                                     BigDecimal received, BigDecimal unprocessed, BigDecimal loss,
                                     StockMeasurement.Weight weight) {
        String actor = actorId == null ? "" : actorId.trim();
        if (actor.isEmpty()) {
            throw new BusinessException("操作人不能为空");
        }
        BigDecimal receivedQty = requiredQty(received);
        BigDecimal unprocessedQty = requiredQty(unprocessed);
        BigDecimal lossQty = requiredQty(loss);
        StockMeasurement.Weight measured = weight == null ? StockMeasurement.unknown() : weight;
        String dispatch = dispatchId == null ? "" : dispatchId.trim();
        String weightText = measured.state() == StockMeasurement.WeightState.KNOWN
                ? measured.value().stripTrailingZeros().toPlainString() : "UNKNOWN";
        String payload = dispatch + "|" + dispatchVersion + "|" + plain(receivedQty) + "|"
                + plain(unprocessedQty) + "|" + plain(lossQty) + "|" + weightText;
        String result = workUnit.execute(new CommandCall(
                requestId, actor, "OUTSOURCE_RETURN", "OUTSOURCE_DISPATCH", dispatch, payload
        ), session -> save(requestId, actor, dispatch, dispatchVersion, receivedQty, unprocessedQty, lossQty, measured, session));
        return returns.find(result).orElseThrow(() -> new IllegalStateException("外协收回保存后无法读取"));
    }

    /** 只读汇总。少回时 pending 为真，settled 为假。 */
    public OutsourcingReturnBalance balance(String dispatchId) {
        OutsourcingDispatch dispatch = dispatches.find(dispatchId).orElseThrow(() -> new BusinessException("外协发外不存在"));
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal unprocessed = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (OutsourcingReturn row : returns.listByDispatch(dispatchId)) {
            received = received.add(row.receivedQty());
            unprocessed = unprocessed.add(row.unprocessedQty());
            loss = loss.add(row.lossQty());
        }
        BigDecimal stillOut = dispatch.quantity().subtract(received).subtract(unprocessed).subtract(loss);
        return new OutsourcingReturnBalance(dispatch.quantity(), received, unprocessed, loss, stillOut,
                stillOut.signum() > 0, stillOut.signum() == 0);
    }

    private String save(String requestId, String actor, String dispatchId, int dispatchVersion,
                        BigDecimal received, BigDecimal unprocessed, BigDecimal loss,
                        StockMeasurement.Weight weight, WorkSession session) {
        OutsourcingDispatch dispatch = dispatches.lock(dispatchId, session)
                .orElseThrow(() -> new BusinessException("外协发外不存在"));
        if (dispatch.version() != dispatchVersion) {
            throw new BusinessException("外协发外已被其他人修改，请刷新后重试");
        }
        if (actor.equals(dispatch.partyId())) {
            throw new BusinessException("经办人不是外协方");
        }
        OutsourcingTask task = sources.findTask(dispatch.taskId()).orElseThrow(() -> new BusinessException("外协任务不存在"));
        StockMeasurement.rejectUndecidedGoldScale(task.businessKind());
        BigDecimal accounted = accounted(dispatch.dispatchId());
        BigDecimal remaining = dispatch.quantity().subtract(accounted);
        BigDecimal taking = received.add(unprocessed).add(loss);
        if (taking.signum() == 0) {
            throw new BusinessException("收回数量必须为正");
        }
        if (taking.compareTo(remaining) > 0) {
            throw new BusinessException("不能超收");
        }
        String outBagId = currentOutBag(dispatch);
        ActivityBag outBag = bags.lock(outBagId, session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (!outBag.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能收回");
        }
        if (outBag.quantity().compareTo(remaining) != 0) {
            throw new BusinessException("外协收回与在外数量不一致");
        }
        BigDecimal stillOut = remaining.subtract(taking);
        Placement placement = place(outBag, received, unprocessed, loss, stillOut, session);
        OutsourcingDispatch next = new OutsourcingDispatch(
                dispatch.dispatchId(), dispatch.requestId(), dispatch.taskId(), dispatch.partyId(),
                dispatch.sourceBagId(), dispatch.sentBagId(), dispatch.quantity(), dispatch.sourceKey(),
                dispatch.ownerType(), dispatch.ownerRef(), dispatch.version() + 1);
        dispatches.update(next, dispatch.version(), session);
        String returnId = "OR" + UUID.randomUUID().toString().replace("-", "");
        OutsourcingReturn row = new OutsourcingReturn(
                returnId, requestId, dispatch.dispatchId(), dispatch.partyId(), next.version(),
                received, unprocessed, loss, weight,
                placement.receivedBagId(), placement.unprocessedBagId(), placement.lossBagId(), placement.remainderBagId());
        returns.save(row, session);
        return returnId;
    }

    private Placement place(ActivityBag bag, BigDecimal received, BigDecimal unprocessed, BigDecimal loss,
                            BigDecimal stillOut, WorkSession session) {
        List<Part> parts = new ArrayList<>();
        if (received.signum() > 0) {
            parts.add(new Part("RECEIVED", received));
        }
        if (unprocessed.signum() > 0) {
            parts.add(new Part("UNPROCESSED", unprocessed));
        }
        if (loss.signum() > 0) {
            parts.add(new Part("LOSS", loss));
        }
        if (stillOut.signum() > 0) {
            parts.add(new Part("OUT", stillOut));
        }
        if (parts.size() == 1 && stillOut.signum() == 0) {
            Part only = parts.get(0);
            if ("LOSS".equals(only.role())) {
                closeBag(bag, bag.quantity(), bag.weight(), session);
                return new Placement("", "", bag.id(), "");
            }
            return "UNPROCESSED".equals(only.role())
                    ? new Placement("", bag.id(), "", "")
                    : new Placement(bag.id(), "", "", "");
        }
        List<String> children = splits.splitWithin(bag.id(), parts.stream().map(Part::quantity).toList(), session);
        String receivedBag = "";
        String unprocessedBag = "";
        String lossBag = "";
        String remainderBag = "";
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            String childId = children.get(i);
            switch (part.role()) {
                case "RECEIVED" -> receivedBag = childId;
                case "UNPROCESSED" -> unprocessedBag = childId;
                case "LOSS" -> {
                    lossBag = childId;
                    closeChild(bag, childId, part.quantity(), session);
                }
                default -> remainderBag = childId;
            }
        }
        return new Placement(receivedBag, unprocessedBag, lossBag, remainderBag);
    }

    private void closeChild(ActivityBag parent, String childId, BigDecimal quantity, WorkSession session) {
        ActivityBag closed = new ActivityBag(
                childId, parent.targetCode(), parent.materialCode(), parent.requirementVersion(),
                parent.sourceKey(), parent.id(), true, quantity, StockMeasurement.unknown(), 2);
        bags.save(closed, 1, session);
    }

    private void closeBag(ActivityBag bag, BigDecimal quantity, StockMeasurement.Weight weight, WorkSession session) {
        ActivityBag closed = new ActivityBag(
                bag.id(), bag.targetCode(), bag.materialCode(), bag.requirementVersion(),
                bag.sourceKey(), bag.parentId(), true, quantity, weight, bag.version() + 1);
        bags.save(closed, bag.version(), session);
    }

    private String currentOutBag(OutsourcingDispatch dispatch) {
        List<OutsourcingReturn> rows = returns.listByDispatch(dispatch.dispatchId());
        if (rows.isEmpty()) {
            return dispatch.sentBagId();
        }
        OutsourcingReturn latest = rows.get(rows.size() - 1);
        if (latest.remainderBagId().isBlank()) {
            throw new BusinessException("该发外已收回，不能再次收回");
        }
        return latest.remainderBagId();
    }

    private BigDecimal accounted(String dispatchId) {
        BigDecimal total = BigDecimal.ZERO;
        for (OutsourcingReturn row : returns.listByDispatch(dispatchId)) {
            total = total.add(row.receivedQty()).add(row.unprocessedQty()).add(row.lossQty());
        }
        return total;
    }

    private static BigDecimal requiredQty(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("收回数量不能为空");
        }
        if (value.signum() < 0) {
            throw new BusinessException("收回数量不能为负");
        }
        return StockMeasurement.processingScale(value);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record Part(String role, BigDecimal quantity) {
    }

    private record Placement(String receivedBagId, String unprocessedBagId, String lossBagId, String remainderBagId) {
    }
}
