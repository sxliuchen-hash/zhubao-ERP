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
        List<String> menuCodes
) {
    public AuthSession {
        menuCodes = List.copyOf(menuCodes);
    }
}
