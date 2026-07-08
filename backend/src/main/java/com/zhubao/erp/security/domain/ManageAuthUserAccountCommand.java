package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

public record ManageAuthUserAccountCommand(
        @NotBlank String username,
        String password,
        @NotBlank String operator,
        @NotBlank String role,
        @NotBlank String dataScope,
        boolean enabled,
        boolean demoAccount
) {
}
