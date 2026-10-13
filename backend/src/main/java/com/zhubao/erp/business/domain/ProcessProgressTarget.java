package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 扫码后的进度目标。已拆母单只返回子单选项，不猜测第一条。
 */
public record ProcessProgressTarget(
        boolean selectChild,
        String orderCode,
        String motherOrderCode,
        String customerName,
        String processingRequirement,
        String dueDate,
        String currentProcess,
        boolean formallyDelivered,
        List<ProcessProgressChildOption> children
) {
    public ProcessProgressTarget {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
