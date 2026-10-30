package com.zhubao.erp.security.domain;

import java.util.Locale;

/**
 * 客户入口账号与稳定客户主体的绑定。只保存客户档案编号，不保存名称或客户自填编号。
 */
public record CustomerPortalBinding(
        String username,
        long customerId,
        boolean enabled,
        String boundBy,
        String boundAt
) {
    public CustomerPortalBinding {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("客户身份缺少账号");
        }
        if (customerId <= 0) {
            throw new IllegalArgumentException("客户绑定不能为空");
        }
        username = username.trim().toLowerCase(Locale.ROOT);
        boundBy = boundBy == null ? "" : boundBy.trim();
        boundAt = boundAt == null ? "" : boundAt.trim();
    }

    public CustomerPortalBinding withEnabled(boolean nextEnabled) {
        return new CustomerPortalBinding(username, customerId, nextEnabled, boundBy, boundAt);
    }
}
