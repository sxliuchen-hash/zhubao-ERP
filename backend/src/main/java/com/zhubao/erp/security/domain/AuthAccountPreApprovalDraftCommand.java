package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AuthAccountPreApprovalDraftCommand(
        @NotBlank String actionType,
        @NotBlank String targetUsername,
        String targetOperator,
        String targetRole,
        String targetDataScope,
        List<String> targetCustomerScope,
        Boolean targetEnabled,
        Boolean demoAccount,
        @NotBlank String reason,
        List<String> providedEvidence
) {
}
