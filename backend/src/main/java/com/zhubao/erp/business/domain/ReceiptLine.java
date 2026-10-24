package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record ReceiptLine(int lineNo, String materialCode, String materialName, String goodsName,
                          BigDecimal quantity, String quantityUnit, BigDecimal weight, String ledgerCode, StorageRequirements storageRequirements) {
    public ReceiptLine(int lineNo, String materialCode, String materialName, String goodsName, java.math.BigDecimal quantity, String quantityUnit, java.math.BigDecimal weight, String ledgerCode) {
        this(lineNo, materialCode, materialName, goodsName, quantity, quantityUnit, weight, ledgerCode, null);
    }
}
