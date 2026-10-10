package com.zhubao.erp.business.domain;

/**
 * 进度一览的一行。数据来自母单、子单、工序交付和质检对照，不另存一份。
 */
public record ProgressRow(
        Long customerId,
        String customerName,
        String motherOrderCode,
        String workOrderCode,
        String currentProcess,
        String dueDate,
        boolean overdue,
        String qualityOutcome,
        String status,
        String openTab
) {
}
