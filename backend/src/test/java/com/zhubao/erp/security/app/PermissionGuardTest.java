package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionGuardTest {

    private final PermissionGuard permissionGuard = new PermissionGuard();

    @Test
    void allowsRoleWithinOperationBoundary() {
        OperatorContext receiver = new OperatorContext("库管员 何琴", RoleCode.WAREHOUSE_KEEPER, DataScope.TEAM);

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

    @Test
    void exposesAllowedPermissionCodesForCurrentRole() {
        assertThat(permissionGuard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER))
                .contains(
                        PermissionCode.BUSINESS_INBOUND_CONFIRM,
                        PermissionCode.BUSINESS_OUTBOUND_SHIPMENT,
                        PermissionCode.BUSINESS_CUSTOMER_RETURN,
                        PermissionCode.REGISTER_IMPORT_REVIEW,
                        PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD,
                        PermissionCode.VIDEO_TASK_OPERATE
                )
                .doesNotContain(PermissionCode.BUSINESS_LOSS_APPROVAL, PermissionCode.AUTH_ACCOUNT_MANAGE);
    }

    @Test
    void allowsOperatorToSubmitMachineOutput() {
        OperatorContext operator = new OperatorContext("雕刻操作工 周凯", RoleCode.OPERATOR, DataScope.SELF);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.MACHINE_OUTPUT_SUBMIT, operator))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsShopFloorRolesToConsumeGatewayReadings() {
        OperatorContext operator = new OperatorContext("操作工 王强", RoleCode.OPERATOR, DataScope.SELF);
        OperatorContext qc = new OperatorContext("质检员 陈曦", RoleCode.QUALITY_INSPECTOR, DataScope.TEAM);
        OperatorContext sales = new OperatorContext("业务专员 林岚", RoleCode.SALES, DataScope.CUSTOMER_RANGE);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.GATEWAY_READING_CONSUME, operator))
                .doesNotThrowAnyException();
        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.GATEWAY_READING_CONSUME, qc))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionGuard.assertAllowed(PermissionCode.GATEWAY_READING_CONSUME, sales))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("SALES");
    }

    @Test
    void limitsGatewayDeviceManagementToAdminAndGeneralManager() {
        OperatorContext admin = new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL);
        OperatorContext operator = new OperatorContext("操作工 王强", RoleCode.OPERATOR, DataScope.SELF);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.GATEWAY_DEVICE_MANAGE, admin))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionGuard.assertAllowed(PermissionCode.GATEWAY_DEVICE_MANAGE, operator))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("设备网关管理");
    }

    @Test
    void limitsCustomerAuthorizationManagementToAdminAndGeneralManager() {
        OperatorContext generalManager = new OperatorContext("总经理", RoleCode.GENERAL_MANAGER, DataScope.ALL);
        OperatorContext sales = new OperatorContext("业务专员 林岚", RoleCode.SALES, DataScope.CUSTOMER_RANGE);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE, generalManager))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionGuard.assertAllowed(PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE, sales))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("客户授权管理");
    }

    @Test
    void limitsAuthAccountManagementToAdminAndGeneralManager() {
        OperatorContext admin = new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL);
        OperatorContext generalManager = new OperatorContext("总经理", RoleCode.GENERAL_MANAGER, DataScope.ALL);
        OperatorContext finance = new OperatorContext("财务统计", RoleCode.FINANCE_STATISTICIAN, DataScope.DEPARTMENT);

        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, admin))
                .doesNotThrowAnyException();
        assertThatCode(() -> permissionGuard.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, generalManager))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionGuard.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, finance))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("FINANCE_STATISTICIAN")
                .hasMessageContaining("登录账号管理");
    }
}
