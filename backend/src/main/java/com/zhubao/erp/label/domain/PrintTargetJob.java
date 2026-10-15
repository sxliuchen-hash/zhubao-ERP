package com.zhubao.erp.label.domain;

public record PrintTargetJob(String id, String batchId, String targetStationId, String targetQueueId,
                             int copies, PrintJobStatus status, String claimedStationId, String leaseUntil,
                             String failureReason, String paperCheckResult, String predecessorId,
                             String retryReason, String createdAt, String updatedAt) {
    public PrintTargetJob withStatus(PrintJobStatus next, String claimed, String lease,
                                     String failure, String paper, String now) {
        return new PrintTargetJob(id, batchId, targetStationId, targetQueueId, copies, next,
                claimed, lease, failure, paper, predecessorId, retryReason, createdAt, now);
    }
}
