package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotNull;

public record AuthUserAccountStatusCommand(
        @NotNull Boolean enabled,
        String reason
) {
}
