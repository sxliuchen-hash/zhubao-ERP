package com.zhubao.erp.security.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ManageAuthUserAccountCommand(
        @NotBlank String username,
        String password,
        @NotBlank String operator,
        @NotBlank String role,
        @NotBlank String dataScope,
        List<String> customerScope,
        boolean enabled,
        boolean demoAccount
) {
    public ManageAuthUserAccountCommand(
            String username,
            String password,
            String operator,
            String role,
            String dataScope,
            boolean enabled,
            boolean demoAccount
    ) {
        this(username, password, operator, role, dataScope, null, enabled, demoAccount);
    }
}
