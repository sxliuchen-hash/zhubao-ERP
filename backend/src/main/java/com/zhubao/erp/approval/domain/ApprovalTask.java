package com.zhubao.erp.approval.domain;

public record ApprovalTask(
        String approvalCode,
        String scene,
        String businessCode,
        String title,
        ApprovalStatus status,
        String applicant,
        String approver,
        String ownerRole,
        String severity,
        String sourceStatus,
        String sourceSummary,
        String evidenceCode,
        boolean formalPayroll,
        String createdAt,
        String decidedAt,
        String decisionOperator,
        String decisionComment,
        boolean locked
) {
}
