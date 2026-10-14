package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 扫码后的进度目标。已拆母单只返回子单选项，不猜测第一条。
 * 具体单据带上当前百分比和版本，提交时用这个版本，不用列表里的旧行。
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
        int percent,
        int version,
        List<ProcessProgressChildOption> children
) {
    public ProcessProgressTarget {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
