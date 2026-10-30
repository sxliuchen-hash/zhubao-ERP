package com.zhubao.erp.security.domain;

/**
 * 一次客户身份变更。账号、绑定和审计必须一起提交。
 */
public record CustomerPortalIdentityAction(
        AuthUserAccount account,
        CustomerPortalBinding binding,
        AuthAuditEvent audit
) {
    public CustomerPortalIdentityAction {
        if (account == null || binding == null || audit == null) {
            throw new IllegalArgumentException("客户身份动作缺少账号、绑定或审计");
        }
        if (!account.username().equals(binding.username())) {
            throw new IllegalArgumentException("客户身份账号与绑定不一致");
        }
    }
}
