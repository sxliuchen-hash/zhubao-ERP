package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthAccountPreApprovalEvidenceSummary(
        String applicationCode,
        String readinessCode,
        String actionType,
        String targetUsername,
        String requiredEvidence,
        String documentCode,
        int attachmentCount,
        int missingEvidence,
        List<String> attachmentCodes,
        String latestUploadedAt,
        String status,
        String nextAction,
        String tone
) {
}
