package com.zhubao.erp.label.domain;

import java.util.List;

public record PrintBatch(String id, String requestId, String businessCode, String templateCode,
                         int copiesPerTarget, String payloadHash, String contentSnapshot,
                         String createdBy, String createdAt, List<PrintTargetJob> jobs) {
    public PrintBatch { jobs = List.copyOf(jobs); }
    public PrintBatch withJobs(List<PrintTargetJob> current) {
        return new PrintBatch(id, requestId, businessCode, templateCode, copiesPerTarget, payloadHash,
                contentSnapshot, createdBy, createdAt, current);
    }
}
