package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record ChildLineAllocation(String childOrderCode, String motherOrderCode, int sourceLineNo,
                                  String materialCode, BigDecimal quantity, BigDecimal weight, String ledgerCode, StorageRequirements storageRequirements) {
    public ChildLineAllocation(String childOrderCode, String motherOrderCode, int sourceLineNo, String materialCode, java.math.BigDecimal quantity, java.math.BigDecimal weight, String ledgerCode) {
        this(childOrderCode, motherOrderCode, sourceLineNo, materialCode, quantity, weight, ledgerCode, null);
    }
}
