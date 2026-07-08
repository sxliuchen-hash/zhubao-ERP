package com.zhubao.erp.security.domain;

import java.time.Instant;

public record AuthUserAccount(
        String username,
        String passwordHash,
        String operator,
        RoleCode role,
        DataScope dataScope,
        boolean enabled,
        boolean demoAccount,
        int failedLoginCount,
        Instant lockedUntil
) {
    public AuthUserAccount(
            String username,
            String passwordHash,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled,
            boolean demoAccount
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, 0, null);
    }
}
