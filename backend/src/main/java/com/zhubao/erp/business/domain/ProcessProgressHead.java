package com.zhubao.erp.business.domain;

/**
 * 一张单某一道工序的当前百分比。历史留在事件表，不覆盖。
 */
public record ProcessProgressHead(
        String orderCode,
        String processName,
        int percent,
        int version,
        String note,
        String operator,
        String updatedAt
) {
}
