package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.EmployeeSettlementAuthorization;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import org.springframework.stereotype.Component;

/**
 * 员工核算只检查工资权限，不因客户账单权限而放行或拦截。
 */
@Component
public class GuardedEmployeeSettlementAuthorization implements EmployeeSettlementAuthorization {

    private final PermissionGuard permissions;

    public GuardedEmployeeSettlementAuthorization(PermissionGuard permissions) {
        this.permissions = permissions;
    }

    @Override
    public void assertCanSettle(OperatorContext actor) {
        permissions.assertAllowed(PermissionCode.FINANCE_PAYROLL, actor);
    }
}
