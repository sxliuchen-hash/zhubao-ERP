package com.zhubao.erp.inventory.app;

import java.math.BigDecimal;
import java.util.List;

/** 库存页面的查询和提交内容。数量用字符串传递，避免小数被截成整数。 */
public final class StockDesk {

    private StockDesk() {
    }

    public record LineInput(
            String materialCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            String externalRef
    ) {
    }

    public record ReceiptInput(
            Long customerId,
            String customerName,
            String warehouseCode,
            String sourceNote,
            String bizDate,
            String handoverPerson,
            String evidenceCode,
            boolean postNow,
            List<LineInput> lines,
            String requestId
    ) {
    }

    public record IssueInput(
            Long customerId,
            String warehouseCode,
            String batchCode,
            String materialCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            String recipient,
            String purpose,
            String targetOrderCode,
            String requestId
    ) {
    }

    public record ReturnInput(
            Long customerId,
            String warehouseCode,
            String batchCode,
            String materialCode,
            String sourceDocumentCode,
            int sourceLineNo,
            BigDecimal quantity,
            BigDecimal weight,
            String requestId
    ) {
    }

    public record CustomerReturnInput(
            Long customerId,
            String warehouseCode,
            String batchCode,
            String materialCode,
            BigDecimal quantity,
            BigDecimal weight,
            String handoverPerson,
            String sourceNote,
            String evidenceCode,
            String requestId
    ) {
    }

    public record OpenOrderInput(
            Long customerId,
            String customerName,
            String warehouseCode,
            String batchCode,
            String materialCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            String goodsName,
            String processingRequirement,
            String dueDate,
            String templateCode,
            int templateVersion,
            String requestId
    ) {
    }

    public record HoldInput(
            String scope,
            Long customerId,
            String warehouseCode,
            String batchCode,
            String materialCode,
            BigDecimal quantity,
            BigDecimal weight,
            String sourceDocumentCode,
            String requestId
    ) {
    }

    public record CorrectionInput(
            String scope,
            Long customerId,
            String warehouseCode,
            String batchCode,
            String materialCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            String direction,
            String reason,
            String requestId
    ) {
    }

    public record ConfirmInput(
            String documentCode,
            List<LineInput> lines,
            String requestId
    ) {
    }

    public record Query(
            String scope,
            Long customerId,
            String materialCode,
            String batchCode,
            String warehouseCode,
            String status,
            String fromDate,
            String toDate,
            String motherOrderCode,
            String documentCode
    ) {
    }

    public record UnitTotal(String unit, String quantity) {
    }

    public record Overview(
            String scope,
            int batchCount,
            List<UnitTotal> quantities,
            String knownWeight,
            int frozenCount,
            int pendingCount
    ) {
    }

    public record BatchCard(
            String scope,
            String ownerLabel,
            String ownerType,
            Long customerId,
            String materialCode,
            String batchCode,
            String warehouseCode,
            String motherOrderCode,
            String workOrderCode,
            String status,
            String quantityUnit,
            String bookQuantity,
            String bookWeight,
            String frozenQuantity,
            String frozenWeight,
            String availableQuantity,
            String availableWeight,
            String issuedQuantity,
            String issuedWeight,
            String weightText,
            boolean pending,
            boolean sourceMissing,
            String documentCode,
            String externalRef,
            String updatedAt,
            int lineNo
    ) {
    }

    public record MovementRow(
            String time,
            String kindLabel,
            String documentCode,
            String source,
            String target,
            String quantity,
            String weight,
            String beforeQuantity,
            String afterQuantity,
            String operatorName
    ) {
    }
}
