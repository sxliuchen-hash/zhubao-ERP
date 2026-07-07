package com.zhubao.erp.business.domain;

import java.util.List;

public final class BusinessReviewData {

    private BusinessReviewData() {
    }

    public record ReviewStat(String label, String value, String detail, String tone) {
    }

    public record ActionItem(String code, String title, String detail, String owner, String action, String tone) {
    }

    public record InboundOrder(
            String code,
            String customer,
            String material,
            String inboundDate,
            String expressCompany,
            String courierNo,
            String senderName,
            String receiverName,
            String unpackSigner,
            String goodsName,
            int quantity,
            String weight,
            String status,
            String videoStatus,
            String ownerType,
            String remark
    ) {
    }

    public record WorkOrder(
            String code,
            String customer,
            String material,
            String process,
            String processCategory,
            String productCategory,
            String machineNo,
            String workItemName,
            String startedAt,
            String finishedAt,
            String completionStatus,
            String handoffAuditStatus,
            String assignee,
            int plannedQuantity,
            int finishedQuantity,
            String status,
            int progress
    ) {
    }

    public record ProductionHandoff(
            String code,
            String fromNode,
            String toNode,
            String inputWeight,
            String outputWeight,
            String lossRate,
            String status,
            String tone
    ) {
    }

    public record QualityCase(
            String code,
            String workOrder,
            String process,
            String inspector,
            String defect,
            String action,
            String status
    ) {
    }

    public record LossApproval(
            String code,
            String workOrder,
            String process,
            String standard,
            String actual,
            String evidence,
            String action,
            String tone
    ) {
    }

    public record InventoryBalance(
            String owner,
            String batch,
            String material,
            String location,
            String status,
            int quantity,
            String weight,
            String frozen
    ) {
    }

    public record InventoryLedgerEntry(
            String time,
            String code,
            String movement,
            String beforeBalance,
            String afterBalance,
            String sourceDocument,
            String operator
    ) {
    }

    public record TraceEvent(String time, String node, String bizCode, String operator, String evidence) {
    }

    public record MobileTask(String code, String scene, String nextAction, String required, String status) {
    }

    public record InboundWorkspaceData(
            List<ReviewStat> checkpoints,
            List<InboundOrder> orders,
            List<ActionItem> risks
    ) {
    }

    public record ProductionWorkspaceData(
            List<ReviewStat> stationLoads,
            List<WorkOrder> workOrders,
            List<ProductionHandoff> handoffs
    ) {
    }

    public record QualityWorkspaceData(
            List<ReviewStat> metrics,
            List<QualityCase> cases,
            List<LossApproval> lossApprovals
    ) {
    }

    public record InventoryWorkspaceData(
            List<InventoryBalance> balances,
            List<InventoryLedgerEntry> ledgerEntries,
            List<TraceEvent> timeline,
            List<ActionItem> evidence
    ) {
    }

    public record MobileWorkspaceData(
            List<MobileTask> tasks,
            List<ReviewStat> capabilities,
            List<ActionItem> offlineDrafts
    ) {
    }

    public record BusinessWorkspaceSummary(
            int inboundOrders,
            int activeWorkOrders,
            int pendingQualityCases,
            int inventoryLedgerEntries,
            int mobileTasks
    ) {
    }
}
