package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record ReceiptLineBalance(int lineNo, String materialCode, String materialName, String goodsName,
                                 BigDecimal quantity, String quantityUnit, BigDecimal weight,
                                 BigDecimal remainingQuantity, BigDecimal remainingWeight, StorageRequirements storageRequirements) {
    public ReceiptLineBalance(int lineNo, String materialCode, String materialName, String goodsName, java.math.BigDecimal quantity, String quantityUnit, java.math.BigDecimal weight, java.math.BigDecimal remainingQuantity, java.math.BigDecimal remainingWeight) {
        this(lineNo, materialCode, materialName, goodsName, quantity, quantityUnit, weight, remainingQuantity, remainingWeight, null);
    }
}
