package com.zhubao.erp.business.domain;

/**
 * 一道工序已经交付。用来判断上一道是否已交，库存数量仍以台账为准。
 */
public record ProcessStationHandoff(
        String orderCode,
        String processName,
        String operator,
        String ledgerCode,
        String createdAt
) {
}
