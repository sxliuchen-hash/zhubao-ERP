package com.zhubao.erp.business.domain;

/** 权限范围内一张执行单的当前工序进度。不把多工序合成母单总百分比。 */
public record ProcessProgressRow(
        String orderCode,
        String motherOrderCode,
        String customerName,
        String currentProcess,
        String businessStatus,
        int percent,
        int version,
        String operator,
        String updatedAt,
        String dueDate
) {
}
