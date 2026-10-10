package com.zhubao.erp.scan.domain;

import java.math.BigDecimal;

/** 一笔可选库存维度。多笔时不能把第一笔当作提交目标。 */
public record ScanBalanceCandidate(
        String materialCode,
        String warehouseCode,
        String ownerType,
        Long ownerCustomerId,
        Long customerId,
        String motherOrderCode,
        String workOrderCode,
        String stockStatus,
        BigDecimal quantity,
        BigDecimal weight
) {
}
