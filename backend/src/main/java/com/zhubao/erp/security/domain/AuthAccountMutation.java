package com.zhubao.erp.security.domain;

/**
 * 一次账号动作要一起提交的变更。申请单为空表示本次没有前置申请需要消费。
 */
public record AuthAccountMutation(
        Kind kind,
        AuthUserAccount account,
        String username,
        Boolean enabled,
        AuthAuditEvent audit,
        AuthAccountPreApprovalApplication application
) {
    public enum Kind {
        SAVE,
        SET_ENABLED,
        UNLOCK
    }

    public AuthAccountMutation {
        if (audit == null) {
            throw new IllegalArgumentException("账号动作必须带管理审计");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("账号动作必须带用户名");
        }
    }

    public static AuthAccountMutation save(
            AuthUserAccount account,
            AuthAuditEvent audit,
            AuthAccountPreApprovalApplication application
    ) {
        return new AuthAccountMutation(Kind.SAVE, account, account.username(), account.enabled(), audit, application);
    }

    public static AuthAccountMutation setEnabled(
            String username,
            boolean enabled,
            AuthAuditEvent audit,
            AuthAccountPreApprovalApplication application
    ) {
        return new AuthAccountMutation(Kind.SET_ENABLED, null, username, enabled, audit, application);
    }

    public static AuthAccountMutation unlock(
            String username,
            AuthAuditEvent audit,
            AuthAccountPreApprovalApplication application
    ) {
        return new AuthAccountMutation(Kind.UNLOCK, null, username, null, audit, application);
    }
}
