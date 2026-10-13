package com.zhubao.erp.business.domain;

/** 一次进度提交或纠错。旧值和新值都保留。 */
public record ProcessProgressEvent(
        long id,
        String orderCode,
        String processName,
        int oldPercent,
        int newPercent,
        String note,
        String reason,
        String operator,
        String createdAt
) {
}
