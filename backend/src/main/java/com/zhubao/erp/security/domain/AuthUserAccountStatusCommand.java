package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotNull;

public record AuthUserAccountStatusCommand(
        @NotNull Boolean enabled,
        String reason,
        String preApprovalApplicationCode
) {
    public AuthUserAccountStatusCommand(Boolean enabled, String reason) {
        this(enabled, reason, null);
    }
}
