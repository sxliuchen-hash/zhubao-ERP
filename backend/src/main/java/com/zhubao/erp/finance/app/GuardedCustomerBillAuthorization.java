package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.CustomerBillAuthorization;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Component;

/**
 * 只检查加工费入账和客户范围，不检查工资权限。
 */
@Component
public class GuardedCustomerBillAuthorization implements CustomerBillAuthorization {

    private final PermissionGuard permissions;

    public GuardedCustomerBillAuthorization(PermissionGuard permissions) {
        this.permissions = permissions;
    }

    @Override
    public void assertCanBill(OperatorContext actor, long customerId) {
        permissions.assertAllowed(PermissionCode.FINANCE_BILLING_POST, actor);
        if (actor.dataScope() == DataScope.CUSTOMER_RANGE) {
            if (actor.customerScope().isEmpty() || !actor.customerScope().contains(Long.toString(customerId))) {
                throw new PermissionDeniedException("不在客户授权范围");
            }
        }
    }
}
