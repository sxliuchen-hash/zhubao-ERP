package com.zhubao.erp.work.domain;

/**
 * 一次用户确认。requestId 在超时和点击重试时保持不变；目标或内容变化必须换新编号。
 */
public record CommandCall(
        String requestId,
        String actorId,
        String action,
        String targetType,
        String targetCode,
        String canonicalPayload
) {
}
