package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessReviewData.ActionItem;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundOrder;
import com.zhubao.erp.business.domain.BusinessReviewData.InboundWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryBalance;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryLedgerEntry;
import com.zhubao.erp.business.domain.BusinessReviewData.InventoryWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.LossApproval;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileTask;
import com.zhubao.erp.business.domain.BusinessReviewData.MobileWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionHandoff;
import com.zhubao.erp.business.domain.BusinessReviewData.ProductionWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityCase;
import com.zhubao.erp.business.domain.BusinessReviewData.QualityWorkspaceData;
import com.zhubao.erp.business.domain.BusinessReviewData.ReviewStat;
import com.zhubao.erp.business.domain.BusinessReviewData.TraceEvent;
import com.zhubao.erp.business.domain.BusinessReviewData.WorkOrder;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

class BusinessWorkspaceDataScopeFilter {

    InboundWorkspaceData filter(InboundWorkspaceData data, OperatorContext context) {
        if (fullAccess(context)) {
            return data;
        }
        List<InboundOrder> orders = data.orders().stream()
                .filter(order -> visible(context, order.code(), order.customer(), order.material(), order.receiverName(),
                        order.unpackSigner(), order.status(), order.videoStatus(), order.ownerType(), order.remark()))
                .toList();
        List<ActionItem> risks = data.risks().stream()
                .filter(actionItem(context))
                .toList();
        return new InboundWorkspaceData(statsIfVisible(data.checkpoints(), orders, risks), orders, risks);
    }

    ProductionWorkspaceData filter(ProductionWorkspaceData data, OperatorContext context) {
        if (fullAccess(context)) {
            return data;
        }
        List<WorkOrder> workOrders = data.workOrders().stream()
                .filter(order -> visible(context, order.code(), order.customer(), order.material(), order.process(),
                        order.processCategory(), order.productCategory(), order.machineNo(), order.workItemName(),
                        order.completionStatus(), order.handoffAuditStatus(), order.assignee(), order.status()))
                .toList();
        List<ProductionHandoff> handoffs = data.handoffs().stream()
                .filter(handoff -> visible(context, handoff.code(), handoff.fromNode(), handoff.toNode(), handoff.status()))
                .toList();
        return new ProductionWorkspaceData(statsIfVisible(data.stationLoads(), workOrders, handoffs), workOrders, handoffs);
    }

    QualityWorkspaceData filter(QualityWorkspaceData data, OperatorContext context) {
        if (fullAccess(context)) {
            return data;
        }
        List<QualityCase> cases = data.cases().stream()
                .filter(qualityCase -> visible(context, qualityCase.code(), qualityCase.workOrder(),
                        qualityCase.process(), qualityCase.inspector(), qualityCase.defect(), qualityCase.action(),
                        qualityCase.status()))
                .toList();
        List<LossApproval> lossApprovals = data.lossApprovals().stream()
                .filter(approval -> visible(context, approval.code(), approval.workOrder(), approval.process(),
                        approval.evidence(), approval.action(), approval.tone()))
                .toList();
        return new QualityWorkspaceData(statsIfVisible(data.metrics(), cases, lossApprovals), cases, lossApprovals);
    }

    InventoryWorkspaceData filter(InventoryWorkspaceData data, OperatorContext context) {
        if (fullAccess(context)) {
            return data;
        }
        List<InventoryBalance> balances = data.balances().stream()
                .filter(balance -> visible(context, balance.owner(), balance.batch(), balance.material(),
                        balance.location(), balance.status(), balance.frozen()))
                .toList();
        List<InventoryLedgerEntry> ledgerEntries = data.ledgerEntries().stream()
                .filter(entry -> visible(context, entry.time(), entry.code(), entry.movement(),
                        entry.sourceDocument(), entry.operator()))
                .toList();
        List<TraceEvent> timeline = data.timeline().stream()
                .filter(event -> visible(context, event.time(), event.node(), event.bizCode(), event.operator(), event.evidence()))
                .toList();
        List<ActionItem> evidence = data.evidence().stream()
                .filter(actionItem(context))
                .toList();
        return new InventoryWorkspaceData(balances, ledgerEntries, timeline, evidence);
    }

    MobileWorkspaceData filter(MobileWorkspaceData data, OperatorContext context) {
        if (fullAccess(context)) {
            return data;
        }
        List<MobileTask> tasks = data.tasks().stream()
                .filter(task -> visible(context, task.code(), task.scene(), task.nextAction(), task.required(), task.status()))
                .toList();
        List<ActionItem> offlineDrafts = data.offlineDrafts().stream()
                .filter(actionItem(context))
                .toList();
        return new MobileWorkspaceData(tasks, statsIfVisible(data.capabilities(), tasks, offlineDrafts), offlineDrafts);
    }

    private Predicate<ActionItem> actionItem(OperatorContext context) {
        return item -> visible(context, item.code(), item.title(), item.detail(), item.owner(), item.action(), item.tone());
    }

    private boolean visible(OperatorContext context, String... values) {
        if (fullAccess(context)) {
            return true;
        }
        return switch (context.dataScope()) {
            case SELF, TEAM, DEPARTMENT -> containsAny(values, operatorKeywords(context));
            case CUSTOMER_RANGE -> !context.customerScope().isEmpty() && containsAny(values, context.customerScope());
            case ALL -> true;
        };
    }

    private boolean fullAccess(OperatorContext context) {
        return context.dataScope() == DataScope.ALL;
    }

    private List<String> operatorKeywords(OperatorContext context) {
        return Arrays.stream(context.operator().split("[\\s/／,，;；-]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private List<ReviewStat> statsIfVisible(List<ReviewStat> stats, List<?>... detailLists) {
        boolean visible = Arrays.stream(detailLists).anyMatch(list -> !list.isEmpty());
        return visible ? stats : List.of();
    }

    private boolean containsAny(String[] values, List<String> keywords) {
        if (keywords.isEmpty()) {
            return false;
        }
        String haystack = Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(haystack::contains);
    }
}
