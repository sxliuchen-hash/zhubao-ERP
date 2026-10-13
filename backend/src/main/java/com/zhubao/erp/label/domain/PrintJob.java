package com.zhubao.erp.label.domain;

/**
 * 服务端生成的标签打印任务。客户端不能上传任意打印指令。
 */
public record PrintJob(
        String id,
        String requestId,
        String businessCode,
        String templateCode,
        int copies,
        String contentSnapshot,
        PrintJobStatus status,
        String stationId,
        String leaseUntil,
        String failureReason,
        String predecessorId,
        String retryReason,
        String createdBy,
        String createdAt,
        String updatedAt
) {
}
