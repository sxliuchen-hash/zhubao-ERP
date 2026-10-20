package com.zhubao.erp.security.domain;

import java.util.List;

public record AuthSession(
        String token,
        String username,
        String operator,
        String role,
        String roleLabel,
        String dataScope,
        String dataScopeLabel,
        String expiresAt,
        boolean demoAccount,
        String passwordChangedAt,
        String passwordExpiresAt,
        boolean passwordExpired,
        boolean passwordChangeRequired,
        List<String> customerScope,
        List<String> menuCodes,
        List<NavigationMenuItem> menuTree,
        List<String> allowedProcesses,
        List<String> coverDuties
) {
    public AuthSession {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        menuCodes = List.copyOf(menuCodes);
        menuTree = List.copyOf(menuTree);
        allowedProcesses = allowedProcesses == null ? List.of() : List.copyOf(allowedProcesses);
        coverDuties = coverDuties == null ? List.of() : List.copyOf(coverDuties);
    }
}
