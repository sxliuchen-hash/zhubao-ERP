package com.zhubao.erp.approval.domain;

import java.util.List;

public record AccountPreApprovalTaskCommand(
        String applicationCode,
        String readinessCode,
        String actionType,
        String targetUsername,
        String applicant,
        String applicantRole,
        String targetOperator,
        String targetRole,
        String targetDataScope,
        List<String> targetCustomerScope,
        String applicationStatus,
        String blockingStatus,
        boolean readyForBlockingApproval,
        List<String> missingEvidence,
        List<String> missingCapabilities,
        List<String> approverRoles,
        String riskLevel,
        String sourceSummary
) {
}
