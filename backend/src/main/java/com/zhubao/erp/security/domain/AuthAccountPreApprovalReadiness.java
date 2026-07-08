package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthAccountPreApprovalReadiness(
        String readinessCode,
        String actionType,
        String scenario,
        String enforcementStatus,
        List<String> requiredBeforeAction,
        List<String> missingCapabilities,
        List<String> approverRoles,
        String auditEvidence,
        String rolloutGate,
        String nextAction,
        String riskLevel,
        String tone
) {
}
