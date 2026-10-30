package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 一次正常整袋报工。进度百分比只是可选记录，不代表交工或工资。
 */
public record WholeBagReport(
        String batchCode,
        String requestId,
        String bagId,
        int bagVersion,
        String targetCode,
        String stepId,
        BigDecimal quantity,
        String attachmentCode,
        String operatorName,
        Integer progressPercent,
        String createdAt
) {
}
