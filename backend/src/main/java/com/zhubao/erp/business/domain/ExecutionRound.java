package com.zhubao.erp.business.domain;

/**
 * 已完成工序需要重做时追加的执行轮次。不删除原来的交付。
 */
public record ExecutionRound(
        String id,
        String motherOrderCode,
        String sourceWorkOrderCode,
        String roundWorkOrderCode,
        String processName,
        String changeId,
        String supersedesOrderCode,
        String createdAt
) {
}