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
        List<String> allowedProcesses,
        List<String> coverDuties,
        String teamCode,
        String departmentCode
) {
    public AuthUserAccount {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        temporaryPasswordConsumed = passwordChangeRequired && temporaryPasswordConsumed;
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
        coverDuties = CoverDuties.normalize(coverDuties);
        teamCode = normalizeOrg(teamCode);
        departmentCode = normalizeOrg(departmentCode);
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
            boolean temporaryPasswordConsumed,
            List<String> allowedProcesses,
            List<String> coverDuties
    ) {
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt,
                failedLoginCount, lockedUntil, customerScope, passwordChangeRequired, temporaryPasswordConsumed,
                allowedProcesses, coverDuties, "", "");
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, List.of(), false, false, List.of(), List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), 0, null, customerScope, false, false, List.of(), List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, Instant.now(), failedLoginCount, lockedUntil, List.of(), false, false, List.of(), List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, List.of(), false, false, List.of(), List.of());
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
        this(username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt, failedLoginCount, lockedUntil, customerScope, false, false, List.of(), List.of());
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
                List.of(),
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
                List.of(),
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
            boolean temporaryPasswordConsumed,
            List<String> allowedProcesses
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
                allowedProcesses,
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
                allowedProcesses,
                coverDuties,
                teamCode,
                departmentCode
        );
    }

    public AuthUserAccount withOrganization(String team, String department) {
        return new AuthUserAccount(
                username, passwordHash, operator, role, dataScope, enabled, demoAccount, passwordChangedAt,
                failedLoginCount, lockedUntil, customerScope, passwordChangeRequired, temporaryPasswordConsumed,
                allowedProcesses, coverDuties, team, department
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
                processes,
                coverDuties,
                teamCode,
                departmentCode
        );
    }

    private static String normalizeOrg(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        if (text.length() > 40 || text.chars().anyMatch(ch -> Character.isISOControl(ch) || ch == '|' || ch == '\n')) {
            throw new com.zhubao.erp.common.error.BusinessException("班组或部门编号无效");
        }
        return text;
    }
}
