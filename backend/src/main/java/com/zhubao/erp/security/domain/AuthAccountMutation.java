package com.zhubao.erp.security.domain;

import java.util.List;

/**
 * 一次账号动作要一起提交的变更。账号、客户授权和审计同次提交。
 */
public record AuthAccountMutation(
        Kind kind,
        AuthUserAccount account,
        String username,
        Boolean enabled,
        AuthAuditEvent audit,
        List<CustomerAuthorization> customerAuthorizations
) {
    public enum Kind {
        SAVE,
        SET_ENABLED,
        UNLOCK,
        DELETE
    }

    public AuthAccountMutation(Kind kind, AuthUserAccount account, String username, Boolean enabled,
                               AuthAuditEvent audit) {
        this(kind, account, username, enabled, audit, null);
    }

    public AuthAccountMutation {
        if (audit == null) {
            throw new IllegalArgumentException("账号动作必须带管理审计");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("账号动作必须带用户名");
        }
        customerAuthorizations = customerAuthorizations == null ? null : List.copyOf(customerAuthorizations);
    }

    public static AuthAccountMutation save(
            AuthUserAccount account,
            AuthAuditEvent audit
    ) {
        return new AuthAccountMutation(Kind.SAVE, account, account.username(), account.enabled(), audit);
    }

    public static AuthAccountMutation setEnabled(
            String username,
            boolean enabled,
            AuthAuditEvent audit
    ) {
        return new AuthAccountMutation(Kind.SET_ENABLED, null, username, enabled, audit);
    }

    public static AuthAccountMutation unlock(
            String username,
            AuthAuditEvent audit
    ) {
        return new AuthAccountMutation(Kind.UNLOCK, null, username, null, audit);
    }

    public static AuthAccountMutation delete(String username, AuthAuditEvent audit) {
        return new AuthAccountMutation(Kind.DELETE, null, username, false, audit, List.of());
    }

    public AuthAccountMutation withCustomerAuthorizations(List<CustomerAuthorization> authorizations) {
        return new AuthAccountMutation(kind, account, username, enabled, audit, authorizations);
    }

    /** 判断本次动作是否会移除现有启用管理员资格。检查必须在仓储锁或数据库事务内完成。 */
    public boolean removesEnabledAdmin() {
        return kind == Kind.DELETE
                || kind == Kind.SET_ENABLED && !Boolean.TRUE.equals(enabled)
                || kind == Kind.SAVE && (!account.enabled() || account.role() != RoleCode.ADMIN);
    }
}
