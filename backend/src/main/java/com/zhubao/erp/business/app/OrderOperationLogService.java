package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.LabelTraceRepository;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 管理员按母单聚合已持久化操作事实，不补造历史操作人或时间。 */
@Service
public class OrderOperationLogService {
    private final BusinessDocumentRepository documents;
    private final ProcessProgressRepository progress;
    private final OrderRequirementRepository requirements;
    private final OrderChangeRepository changes;
    private final LabelTraceRepository labels;
    private final OperationProgressRepository notes;
    private final PermissionGuard permissions;

    public OrderOperationLogService(BusinessDocumentRepository documents, ProcessProgressRepository progress,
                                    OrderRequirementRepository requirements, OrderChangeRepository changes,
                                    LabelTraceRepository labels, OperationProgressRepository notes, PermissionGuard permissions) {
        this.documents = documents;
        this.progress = progress;
        this.requirements = requirements;
        this.changes = changes;
        this.labels = labels;
        this.notes = notes;
        this.permissions = permissions;
    }

    public record Entry(String targetCode, String action, String operator, String createdAt, String detail) { }

    public List<Entry> list(String code, OperatorContext operator) {
        permissions.assertAllowed(PermissionCode.ORDER_ADMIN_MAINTAIN, operator);
        String motherCode = documents.findMotherOrderAny(code).map(MotherOrderRecord::code)
                .orElseGet(() -> documents.findChildWorkOrderAny(code).map(ChildWorkOrderRecord::motherOrderCode)
                        .orElseGet(() -> requirements.findRound(code).map(ExecutionRound::motherOrderCode)
                                .orElseThrow(() -> new BusinessException("没有这张订单"))));
        MotherOrderRecord mother = documents.findMotherOrderAny(motherCode)
                .orElseThrow(() -> new BusinessException("母单不存在"));
        List<Entry> rows = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        targets.add(motherCode);
        rows.add(new Entry(motherCode, "创建母单", mother.operator(), mother.createdAt(), mother.goodsName()));
        documents.findMotherSplitState(motherCode).ifPresent(state -> rows.add(new Entry(motherCode,
                MotherSplitState.KEEP.equals(state.decision()) ? "确认不拆单" : "确认拆单", state.operator(), state.createdAt(), "")));
        for (ChildWorkOrderRecord child : documents.listChildWorkOrdersAny(motherCode)) {
            targets.add(child.code());
            rows.add(new Entry(child.code(), "创建子单", child.operator(), child.createdAt(), child.processes()));
        }
        for (ExecutionRound round : requirements.listByMother(motherCode)) {
            targets.add(round.roundWorkOrderCode());
            rows.add(new Entry(round.roundWorkOrderCode(), "创建执行轮次", "", round.createdAt(),
                    "原单：" + round.sourceWorkOrderCode() + "；工序：" + round.processName()));
        }
        for (String target : targets) {
            String type = target.equals(motherCode) ? "MOTHER" : "WORK_ORDER";
            for (OrderAdjustment adjustment : documents.listOrderAdjustments(type, target)) {
                rows.add(new Entry(target, adjustmentAction(adjustment.reason()), adjustment.operatorName(), adjustment.createdAt(),
                        adjustment.reason() + "；" + adjustment.beforeText() + " → " + adjustment.afterText()));
            }
            for (String process : WorkshopProcesses.DEFAULT_ORDER) {
                for (ProcessProgressEvent event : progress.history(target, process)) rows.add(new Entry(target, "登记工序进度",
                        event.operator(), event.createdAt(), event.processName() + "：" + event.oldPercent() + "% → "
                        + event.newPercent() + "%；" + event.note() + "；" + event.reason()));
            }
            for (ProcessStationHandoff handoff : documents.listProcessStationHandoffs(target)) rows.add(new Entry(target,
                    "正式交工", handoff.operator(), handoff.createdAt(), handoff.processName() + "；流水：" + handoff.ledgerCode()));
            for (QualityRound round : documents.listQualityRounds(target)) rows.add(new Entry(target, "质检",
                    round.inspector(), round.createdAt(), "第 " + round.roundNo() + " 轮：" + round.outcome()));
            var check = documents.findQualityRequirementCheck(target).orElse(null);
            if (check != null) {
                if (documents.listQualityRounds(target).isEmpty()) rows.add(new Entry(target, "质检", check.inspector(), check.createdAt(), check.outcome()));
                documents.findQualityReworkReport(check.reworkOrderCode()).ifPresent(report -> rows.add(new Entry(target,
                        "返工报工", report.operator(), report.createdAt(), report.reworkOrderCode() + "；流水：" + report.ledgerCode())));
            }
            String scanType = target.equals(motherCode) ? "MOTHER_ORDER" : "WORK_ORDER";
            notes.findByTarget(scanType, target).forEach(note -> rows.add(new Entry(target, "过程说明", note.operatorName(), note.recordedAt(), note.note())));
        }
        documents.listAllProcessBatches().stream().filter(batch -> targets.contains(batch.orderCode())).forEach(batch -> rows.add(
                new Entry(batch.orderCode(), "分批交工", batch.operator(), batch.createdAt(), batch.processName()
                        + "；批次：" + batch.batchCode() + "；数量：" + batch.quantity() + "；总重量：" + batch.finalWeight())));
        documents.listOutboundOrders().stream().filter(order -> motherCode.equals(order.motherOrderCode())).forEach(order -> rows.add(
                new Entry(order.code(), "确认发货", order.operator(), order.createdAt(), "原单：" + order.workOrderCode() + "；物流：" + order.logisticsNo())));
        changes.listByMother(motherCode).forEach(change -> {
            rows.add(new Entry(text(change.workOrderCode()).isBlank() ? motherCode : change.workOrderCode(), "创建改单",
                    change.createdBy(), change.createdAt(), change.id() + "；" + change.oldRequirement() + " → " + change.newRequirement()));
            rows.add(new Entry(text(change.workOrderCode()).isBlank() ? motherCode : change.workOrderCode(), "改单当前状态",
                    change.confirmedBy(), change.updatedAt(), change.id() + "；" + changeStatus(change.status())));
        });
        labels.listByMother(motherCode).forEach(label -> rows.add(new Entry(label.businessCode(), "标签操作",
                label.operatorName(), label.createdAt(), label.eventType() + "；" + label.reason())));
        return rows.stream().sorted(Comparator.comparing((Entry entry) -> text(entry.createdAt()).replace(' ', 'T'))
                .thenComparing(Entry::targetCode).thenComparing(Entry::action)).toList();
    }

    private static String adjustmentAction(String reason) {
        if ("编辑加工要求".equals(reason) || "撤销整单生产".equals(reason)) {
            return reason;
        }
        return "管理员维护";
    }

    private String changeStatus(OrderChangeStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PENDING_FINANCE -> "待财务确认";
            case FINANCE_CONFIRMED -> "财务已确认";
            case DEFERRED_TO_SETTLEMENT -> "费用交货后确定";
            case PENDING_REVIEW -> "待业务审核";
            case BUSINESS_APPROVED -> "业务审核通过";
            case APPLIED -> "已生效";
            case REJECTED -> "已驳回";
            case CANCELED -> "已取消";
        };
    }
    private static String text(String value) { return value == null ? "" : value; }
}
