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
        String approvalTaskCode,
        String submittedAt,
        List<String> targetAllowedProcesses,
        String statusAction
) {
    public AuthAccountPreApprovalApplication(String applicationCode, String draftCode, String readinessCode, String actionType, String targetUsername, String applicant, String applicantRole, String targetOperator, String targetRole, String targetDataScope, List<String> targetCustomerScope, Boolean targetEnabled, Boolean demoAccount, String reason, List<String> requiredEvidence, List<String> providedEvidence, List<String> missingEvidence, List<String> missingCapabilities, List<String> approverRoles, String blockingStatus, String applicationStatus, boolean readyForBlockingApproval, String sourceSummary, String nextAction, String riskLevel, String tone, String approvalTaskCode, String submittedAt) {
        this(applicationCode, draftCode, readinessCode, actionType, targetUsername, applicant, applicantRole, targetOperator, targetRole, targetDataScope, targetCustomerScope, targetEnabled, demoAccount, reason, requiredEvidence, providedEvidence, missingEvidence, missingCapabilities, approverRoles, blockingStatus, applicationStatus, readyForBlockingApproval, sourceSummary, nextAction, riskLevel, tone, approvalTaskCode, submittedAt, List.of(), "");
    }

    /**
     * 账号动作提交成功后的申请单。只在同一事务内替换原可执行状态。
     */
    public AuthAccountPreApprovalApplication executed() {
        return new AuthAccountPreApprovalApplication(
                applicationCode,
                draftCode,
                readinessCode,
                actionType,
                targetUsername,
                applicant,
                applicantRole,
                targetOperator,
                targetRole,
                targetDataScope,
                targetCustomerScope,
                targetEnabled,
                demoAccount,
                reason,
                requiredEvidence,
                providedEvidence,
                missingEvidence,
                missingCapabilities,
                approverRoles,
                blockingStatus,
                "EXECUTED",
                false,
                sourceSummary,
                "账号动作已执行，后续变更须重新申请",
                riskLevel,
                tone,
                approvalTaskCode,
                submittedAt,
                targetAllowedProcesses,
                statusAction
        );
    }

    public boolean executable() {
        return "READY_FOR_BLOCKING_APPROVAL".equals(applicationStatus) && readyForBlockingApproval;
    }
}
