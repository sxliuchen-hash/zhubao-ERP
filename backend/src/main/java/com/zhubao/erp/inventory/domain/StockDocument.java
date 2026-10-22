package com.zhubao.erp.inventory.domain;

import java.util.List;

/** 公司料或客户寄存的正式单据。请求编号用于识别同一次提交。 */
public record StockDocument(
        String documentCode,
        String kind,
        String status,
        String ownerType,
        Long customerId,
        String customerName,
        String warehouseCode,
        String sourceNote,
        String handoverPerson,
        String purpose,
        String recipient,
        String targetOrderCode,
        String sourceDocumentCode,
        String operatorName,
        String requestId,
        String bizDate,
        String createdAt,
        String evidenceCode,
        String direction,
        List<StockDocumentLine> lines
) {
    public StockDocument {
        lines = lines == null ? List.of() : List.copyOf(lines);
        customerName = customerName == null ? "" : customerName;
        sourceNote = sourceNote == null ? "" : sourceNote;
        handoverPerson = handoverPerson == null ? "" : handoverPerson;
        purpose = purpose == null ? "" : purpose;
        recipient = recipient == null ? "" : recipient;
        targetOrderCode = targetOrderCode == null ? "" : targetOrderCode;
        sourceDocumentCode = sourceDocumentCode == null ? "" : sourceDocumentCode;
        evidenceCode = evidenceCode == null ? "" : evidenceCode;
        direction = direction == null ? "" : direction;
    }
}
