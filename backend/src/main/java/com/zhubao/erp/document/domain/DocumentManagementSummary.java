package com.zhubao.erp.document.domain;

public record DocumentManagementSummary(
        int packages,
        int attachments,
        int missingEvidence,
        int manualFallbacks,
        int auditEvents
) {
}
