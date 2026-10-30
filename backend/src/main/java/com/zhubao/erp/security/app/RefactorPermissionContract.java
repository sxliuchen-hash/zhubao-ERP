package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;

/**
 * 重构新增动作的授权边界。客户范围只用于读取；新动作不自动并入原有岗位。
 */
public final class RefactorPermissionContract {

    private RefactorPermissionContract() {
    }

    public static void assertCustomerRead(OperatorContext operator, Long customerId) {
        if (operator == null || operator.role() == null) {
            throw new PermissionDeniedException("缺少登录身份");
        }
        if (operator.dataScope() == DataScope.CUSTOMER_RANGE
                && (operator.customerScope() == null || operator.customerScope().isEmpty())) {
            throw new PermissionDeniedException("客户范围账号没有绑定客户");
        }
        if (!CustomerScopeRules.allowed(operator, customerId, "")) {
            throw new PermissionDeniedException("客户不在当前账号范围内");
        }
    }

    public static void assertAction(PermissionGuard guard, PermissionCode permission, OperatorContext operator) {
        if (operator == null || operator.role() == null) {
            throw new PermissionDeniedException("缺少登录身份");
        }
        if (operator.role() == RoleCode.BUSINESS_MANAGER
                && (operator.coverDuties() == null || operator.coverDuties().isEmpty())) {
            throw new PermissionDeniedException("业务管理缺少顶岗，不能办理");
        }
        guard.assertAllowed(permission, operator);
    }
}
