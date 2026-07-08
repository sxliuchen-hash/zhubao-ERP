package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthAccountPreApprovalDraft(
        String draftCode,
        String readinessCode,
        String actionType,
        String targetUsername,
        String applicant,
        String applicantRole,
        List<String> requiredEvidence,
        List<String> providedEvidence,
        List<String> missingEvidence,
        List<String> missingCapabilities,
        List<String> approverRoles,
        String blockingStatus,
        boolean readyForBlockingApproval,
        String sourceSummary,
        String nextAction,
        String riskLevel,
        String tone
) {
}
