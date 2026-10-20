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
        boolean passwordChangeRequired,
        int failedLoginCount,
        String lockedUntil,
        boolean locked,
        List<String> allowedProcesses,
        List<String> coverDuties
) {
    public AuthUserAccountSummary {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
        coverDuties = coverDuties == null ? List.of() : List.copyOf(coverDuties);
    }
}
