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
        boolean passwordChangeRequired,
        boolean temporaryPasswordConsumed,
        List<String> allowedProcesses
) {
    public AuthUserAccount {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        temporaryPasswordConsumed = passwordChangeRequired && temporaryPasswordConsumed;
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, List.of(), false, false, List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, customerScope, false, false, List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), failedLoginCount, lockedUntil, List.of(), false, false, List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, List.of(), false, false, List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, customerScope, false, false, List.of());
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
            List<String> customerScope,
            boolean passwordChangeRequired
    ) {
        this(
                username,
                passwordHash,
                operator,
                role,
                dataScope,
                enabled,
                demoAccount,
                passwordChangedAt,
                failedLoginCount,
                lockedUntil,
                customerScope,
                passwordChangeRequired,
                false,
                List.of()
        );
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
            List<String> customerScope,
            boolean passwordChangeRequired,
            boolean temporaryPasswordConsumed
    ) {
        this(
                username,
                passwordHash,
                operator,
                role,
                dataScope,
                enabled,
                demoAccount,
                passwordChangedAt,
                failedLoginCount,
                lockedUntil,
                customerScope,
                passwordChangeRequired,
                temporaryPasswordConsumed,
                List.of()
        );
    }

    public AuthUserAccount withCustomerScope(List<String> scope) {
        return new AuthUserAccount(
                username,
                passwordHash,
                operator,
                role,
                dataScope,
                enabled,
                demoAccount,
                passwordChangedAt,
                failedLoginCount,
                lockedUntil,
                scope,
                passwordChangeRequired,
                temporaryPasswordConsumed,
                allowedProcesses
        );
    }

    public AuthUserAccount withAllowedProcesses(List<String> processes) {
        return new AuthUserAccount(
                username,
                passwordHash,
                operator,
                role,
                dataScope,
                enabled,
                demoAccount,
                passwordChangedAt,
                failedLoginCount,
                lockedUntil,
                customerScope,
                passwordChangeRequired,
                temporaryPasswordConsumed,
                processes
        );
    }
}
