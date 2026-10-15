package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record ReceiptLine(int lineNo, String materialCode, String materialName, String goodsName,
                          BigDecimal quantity, String quantityUnit, BigDecimal weight, String ledgerCode) {
}
