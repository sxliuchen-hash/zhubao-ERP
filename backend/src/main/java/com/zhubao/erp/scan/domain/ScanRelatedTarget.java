package com.zhubao.erp.scan.domain;

import java.math.BigDecimal;

/** 授权范围内可供选择的关联对象。 */
public record ScanRelatedTarget(
        ScanObjectType objectType,
        String code,
        String motherOrderCode,
        String requirement,
        BigDecimal quantity,
        String quantityUnit
) {
}
