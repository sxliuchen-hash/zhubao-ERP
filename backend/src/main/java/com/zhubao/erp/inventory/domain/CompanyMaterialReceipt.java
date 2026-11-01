package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/**
 * 公司珠料或配件的一次入库。权属保持公司，批次和金额来源分开保存。
 */
public record CompanyMaterialReceipt(
        String receiptId,
        String itemId,
        String batchCode,
        BigDecimal quantity,
        String unit,
        StockMeasurement.Weight weight,
        String warehouseCode,
        CompanyMaterialSourceKind sourceKind,
        String supplierName,
        CompanyMaterialAmountState amountState,
        BigDecimal amount,
        String payableCode,
        String coveredPayableCode,
        int version,
        String businessDate,
        String recordedAt
) {
    public CompanyMaterialReceipt(String receiptId, String itemId, String batchCode, BigDecimal quantity,
                                  String unit, StockMeasurement.Weight weight, String warehouseCode,
                                  CompanyMaterialSourceKind sourceKind, String supplierName,
                                  CompanyMaterialAmountState amountState, BigDecimal amount, String payableCode,
                                  String coveredPayableCode, int version) {
        this(receiptId, itemId, batchCode, quantity, unit, weight, warehouseCode, sourceKind, supplierName,
                amountState, amount, payableCode, coveredPayableCode, version, null, null);
    }
}
