package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthAccountPreApprovalApplication(
        String applicationCode,
        String draftCode,
        String readinessCode,
        String actionType,
        String targetUsername,
        String applicant,
        String applicantRole,
        String targetOperator,
        String targetRole,
        String targetDataScope,
        List<String> targetCustomerScope,
        Boolean targetEnabled,
        Boolean demoAccount,
        String reason,
        List<String> requiredEvidence,
        List<String> providedEvidence,
        List<String> missingEvidence,
        List<String> missingCapabilities,
        List<String> approverRoles,
        String blockingStatus,
        String applicationStatus,
        boolean readyForBlockingApproval,
        String sourceSummary,
        String nextAction,
        String riskLevel,
        String tone,
        String submittedAt
) {
}
