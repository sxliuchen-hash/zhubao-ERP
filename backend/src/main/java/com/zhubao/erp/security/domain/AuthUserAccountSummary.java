package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthUserAccountSummary(
        String username,
        String operator,
        String role,
        String roleLabel,
        String dataScope,
        String dataScopeLabel,
        List<String> customerScope,
        boolean enabled,
        boolean demoAccount,
        String passwordChangedAt,
        String passwordExpiresAt,
        boolean passwordExpired,
        int failedLoginCount,
        String lockedUntil,
        boolean locked
) {
    public AuthUserAccountSummary {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
    }
}
