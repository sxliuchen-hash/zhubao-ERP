package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 库存单据明细。重量为空表示尚未称量，不能计入可用库存。 */
public record StockDocumentLine(
        int lineNo,
        String materialCode,
        String quantityUnit,
        BigDecimal quantity,
        BigDecimal weight,
        String batchCode,
        String externalRef,
        Integer sourceLineNo,
        boolean counted,
        BigDecimal declaredQuantity,
        BigDecimal declaredWeight,
        String ledgerCode,
        com.zhubao.erp.business.domain.StorageRequirements storageRequirements
) {
    public StockDocumentLine(int lineNo, String materialCode, String quantityUnit, BigDecimal quantity, BigDecimal weight,
                             String batchCode, String externalRef, Integer sourceLineNo, boolean counted,
                             BigDecimal declaredQuantity, BigDecimal declaredWeight, String ledgerCode) {
        this(lineNo, materialCode, quantityUnit, quantity, weight, batchCode, externalRef, sourceLineNo, counted,
                declaredQuantity, declaredWeight, ledgerCode, null);
    }
    public StockDocumentLine {
        batchCode = batchCode == null ? "" : batchCode;
        externalRef = externalRef == null ? "" : externalRef;
        ledgerCode = ledgerCode == null ? "" : ledgerCode;
        quantityUnit = quantityUnit == null ? "" : quantityUnit;
    }
}
