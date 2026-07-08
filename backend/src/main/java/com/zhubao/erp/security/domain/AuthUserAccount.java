package com.zhubao.erp.security.domain;

import java.time.Instant;
import java.util.List;

public record AuthUserAccount(
        String username,
        String passwordHash,
        String operator,
        RoleCode role,
        DataScope dataScope,
        boolean enabled,
        boolean demoAccount,
        Instant passwordChangedAt,
        int failedLoginCount,
        Instant lockedUntil,
        List<String> customerScope,
        boolean passwordChangeRequired
) {
    public AuthUserAccount {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
    }

    public AuthUserAccount(
            String username,
            String passwordHash,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled,
            boolean demoAccount
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, List.of(), false);
    }

    public AuthUserAccount(
            String username,
            String passwordHash,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled,
            boolean demoAccount,
            List<String> customerScope
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, customerScope, false);
    }

    public AuthUserAccount(
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), failedLoginCount, lockedUntil, List.of(), false);
    }

    public AuthUserAccount(
            String username,
            String passwordHash,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled,
            boolean demoAccount,
            Instant passwordChangedAt,
            int failedLoginCount,
            Instant lockedUntil
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, List.of(), false);
    }

    public AuthUserAccount(
            String username,
            String passwordHash,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled,
            boolean demoAccount,
            Instant passwordChangedAt,
            int failedLoginCount,
            Instant lockedUntil,
            List<String> customerScope
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, customerScope, false);
    }
}
