package com.zhubao.erp.work.domain;

/**
 * 已占用的命令请求。结果为空表示事务尚未提交。
 */
public record CommandRequest(
        String requestId,
        String actorId,
        String action,
        String targetType,
        String targetCode,
        String payloadHash,
        String resultText
) {
}
