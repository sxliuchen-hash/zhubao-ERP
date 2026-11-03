package com.zhubao.erp.security.domain;

import com.zhubao.erp.security.error.PermissionDeniedException;

/**
 * 客户入口身份的读取边界。内部全部数据权限和顶岗都不能当成客户身份使用。
 */
public final class CustomerPortalRules {

    /** 未签发凭据的占位。不能用来登录，也不表示某个默认口令。 */
    public static final String UNISSUED_PASSWORD = "portal-credential-unissued";

    private CustomerPortalRules() {
    }

    public static void assertRestricted(AuthUserAccount account, CustomerPortalBinding binding) {
        if (account == null || account.role() != RoleCode.CUSTOMER_PORTAL) {
            throw new PermissionDeniedException("不是客户身份");
        }
        if (!account.enabled() || binding == null || !binding.enabled()) {
            throw new PermissionDeniedException("客户身份已停用");
        }
        if (account.dataScope() != DataScope.CUSTOMER_RANGE
                || !account.coverDuties().isEmpty()
                || !account.allowedProcesses().isEmpty()) {
            throw new PermissionDeniedException("客户身份不能携带内部全部数据权限或顶岗");
        }
        String customerId = Long.toString(binding.customerId());
        if (!account.username().equals(binding.username())
                || account.customerScope().size() != 1
                || !customerId.equals(account.customerScope().get(0))) {
            throw new PermissionDeniedException("客户身份只能绑定一个客户主体");
        }
    }
}
