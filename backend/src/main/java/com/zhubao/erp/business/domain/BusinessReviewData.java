package com.zhubao.erp.business.domain;

import java.util.List;
import java.math.BigDecimal;

public final class BusinessReviewData {

    private BusinessReviewData() {
    }

    /**
     * 操作所需的真实库存维度与当前余额。无法唯一确定来源库存时不提供上下文。
     */
    public record OperationContext(
            Long customerId,
            Long ownerCustomerId,
            String motherOrderCode,
            String workOrderCode,
            String materialCode,
            String warehouseCode,
            String ownerType,
            String stockStatus,
            BigDecimal quantity,
            BigDecimal weight
    ) {
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
            String remark,
            OperationContext operationContext
    ) {
        public InboundOrder(String code, String customer, String material, String inboundDate, String expressCompany, String courierNo, String senderName, String receiverName, String unpackSigner, String goodsName, int quantity, String weight, String status, String videoStatus, String ownerType, String remark) {
            this(code, customer, material, inboundDate, expressCompany, courierNo, senderName, receiverName, unpackSigner, goodsName, quantity, weight, status, videoStatus, ownerType, remark, null);
        }
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
            int progress,
            OperationContext operationContext
    ) {
        public WorkOrder(String code, String customer, String material, String process, String processCategory, String productCategory, String machineNo, String workItemName, String startedAt, String finishedAt, String completionStatus, String handoffAuditStatus, String assignee, int plannedQuantity, int finishedQuantity, String status, int progress) {
            this(code, customer, material, process, processCategory, productCategory, machineNo, workItemName, startedAt, finishedAt, completionStatus, handoffAuditStatus, assignee, plannedQuantity, finishedQuantity, status, progress, null);
        }
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
            String status,
            OperationContext operationContext
    ) {
        public QualityCase(String code, String workOrder, String process, String inspector, String defect, String action, String status) {
            this(code, workOrder, process, inspector, defect, action, status, null);
        }
    }

    public record LossApproval(
            String code,
            String workOrder,
            String process,
            String standard,
            String actual,
            String evidence,
            String action,
            String tone,
            OperationContext operationContext
    ) {
        public LossApproval(String code, String workOrder, String process, String standard, String actual, String evidence, String action, String tone) {
            this(code, workOrder, process, standard, actual, evidence, action, tone, null);
        }
    }

    public record InventoryBalance(
            String owner,
            String batch,
            String material,
            String location,
            String status,
            String quantity,
            String weight,
            String frozen,
            OperationContext operationContext,
            String quantityUnit
    ) {
        public InventoryBalance {
            quantityUnit = quantityUnit == null ? "" : quantityUnit;
        }

        public InventoryBalance(String owner, String batch, String material, String location, String status, int quantity, String weight, String frozen) {
            this(owner, batch, material, location, status, Integer.toString(quantity), weight, frozen, null, "");
        }

        public InventoryBalance(String owner, String batch, String material, String location, String status, int quantity, String weight, String frozen, OperationContext operationContext) {
            this(owner, batch, material, location, status, Integer.toString(quantity), weight, frozen, operationContext, "");
        }
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
            List<ActionItem> risks,
            String dataSource
    ) {
        public InboundWorkspaceData(List<ReviewStat> checkpoints, List<InboundOrder> orders, List<ActionItem> risks) {
            this(checkpoints, orders, risks, "DEMO");
        }
    }

    public record ProductionWorkspaceData(
            List<ReviewStat> stationLoads,
            List<WorkOrder> workOrders,
            List<ProductionHandoff> handoffs,
            String dataSource
    ) {
        public ProductionWorkspaceData(List<ReviewStat> stationLoads, List<WorkOrder> workOrders, List<ProductionHandoff> handoffs) {
            this(stationLoads, workOrders, handoffs, "DEMO");
        }
    }

    public record QualityWorkspaceData(
            List<ReviewStat> metrics,
            List<QualityCase> cases,
            List<LossApproval> lossApprovals,
            String dataSource
    ) {
        public QualityWorkspaceData(List<ReviewStat> metrics, List<QualityCase> cases, List<LossApproval> lossApprovals) {
            this(metrics, cases, lossApprovals, "DEMO");
        }
    }

    public record InventoryWorkspaceData(
            List<InventoryBalance> balances,
            List<InventoryLedgerEntry> ledgerEntries,
            List<TraceEvent> timeline,
            List<ActionItem> evidence,
            String dataSource
    ) {
        public InventoryWorkspaceData(List<InventoryBalance> balances, List<InventoryLedgerEntry> ledgerEntries, List<TraceEvent> timeline, List<ActionItem> evidence) {
            this(balances, ledgerEntries, timeline, evidence, "DEMO");
        }
    }

    public record MobileWorkspaceData(
            List<MobileTask> tasks,
            List<ReviewStat> capabilities,
            List<ActionItem> offlineDrafts,
            String dataSource
    ) {
        public MobileWorkspaceData(List<MobileTask> tasks, List<ReviewStat> capabilities, List<ActionItem> offlineDrafts) {
            this(tasks, capabilities, offlineDrafts, "DEMO");
        }
    }

    public record BusinessWorkspaceSummary(
            int inboundOrders,
            int activeWorkOrders,
            int pendingQualityCases,
            int inventoryLedgerEntries,
            int mobileTasks,
            String dataSource
    ) {
        public BusinessWorkspaceSummary(int inboundOrders, int activeWorkOrders, int pendingQualityCases, int inventoryLedgerEntries, int mobileTasks) {
            this(inboundOrders, activeWorkOrders, pendingQualityCases, inventoryLedgerEntries, mobileTasks, "DEMO");
        }
    }
}
