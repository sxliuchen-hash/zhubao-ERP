package com.zhubao.erp.scan.domain;

import java.math.BigDecimal;

/**
 * 权限内可见的单据事实。未知项保持空，不补件数或重量。
 */
public record ScanContextOrder(
        Long customerId,
        String customerName,
        String goodsName,
        String materialCode,
        BigDecimal quantity,
        String quantityUnit,
        BigDecimal weight,
        String weightUnit,
        String requirement,
        String dueDate
) {
}
