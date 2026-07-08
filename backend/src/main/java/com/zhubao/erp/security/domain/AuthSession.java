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
        List<String> menuCodes,
        List<NavigationMenuItem> menuTree
) {
    public AuthSession {
        menuCodes = List.copyOf(menuCodes);
        menuTree = List.copyOf(menuTree);
    }
}
