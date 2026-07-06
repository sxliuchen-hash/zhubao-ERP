package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionGuardTest {

    private final PermissionGuard permissionGuard = new PermissionGuard();

    @Test
    void allowsRoleWithinOperationBoundary() {
        OperatorContext receiver = new OperatorContext("库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.TEAM);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.BUSINESS_INBOUND_CONFIRM, receiver))
                .doesNotThrowAnyException();
    }

    @Test
    void deniesRoleOutsideOperationBoundary() {
        OperatorContext operator = new OperatorContext("操作工 王强", RoleCode.OPERATOR, DataScope.SELF);

        assertThatThrownBy(() -> permissionGuard.assertAllowed(PermissionCode.BUSINESS_LOSS_APPROVAL, operator))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("OPERATOR")
                .hasMessageContaining("损耗审批试算");
    }
}
