package com.zhubao.erp.security.domain;

/**
 * 登录当时的账号快照。密码、角色、范围或工序变化后，旧令牌不能继续使用。
 */
public final class AuthAccountSnapshot {

    private AuthAccountSnapshot() {
    }

    public static String fingerprint(AuthUserAccount user) {
        String canonical = String.join("\n",
                text(user.passwordHash()),
                user.passwordChangedAt() == null ? "" : user.passwordChangedAt().toString(),
                user.role().name(),
                user.dataScope().name(),
                text(user.operator()),
                Boolean.toString(user.demoAccount()),
                Boolean.toString(user.passwordChangeRequired()),
                String.join("|", user.customerScope()),
                String.join("|", user.allowedProcesses())
        );
        return AuthTokenDigest.sha256(canonical);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
