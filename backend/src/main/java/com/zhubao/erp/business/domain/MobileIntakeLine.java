package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record MobileIntakeLine(String materialCode, String goodsName, BigDecimal quantity,
                               String quantityUnit, BigDecimal weight) {
}
