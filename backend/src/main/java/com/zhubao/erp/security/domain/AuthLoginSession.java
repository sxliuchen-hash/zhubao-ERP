package com.zhubao.erp.security.domain;

import java.time.Instant;

/**
 * 已建立的登录会话。只保留令牌摘要和账号快照指纹，不保留原始令牌或密码。
 */
public record AuthLoginSession(
        String tokenHash,
        String username,
        String operator,
        String role,
        Instant expiresAt,
        String accountFingerprint,
        String tokenSuffix
) {
}
