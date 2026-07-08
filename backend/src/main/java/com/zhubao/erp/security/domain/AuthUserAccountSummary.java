package com.zhubao.erp.security.domain;

public record AuthUserAccountSummary(
        String username,
        String operator,
        String role,
        String roleLabel,
        String dataScope,
        String dataScopeLabel,
        boolean enabled,
        boolean demoAccount,
        int failedLoginCount,
        String lockedUntil,
        boolean locked
) {
}
