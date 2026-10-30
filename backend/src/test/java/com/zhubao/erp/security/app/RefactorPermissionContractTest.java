package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefactorPermissionContractTest {

    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 新动作不进入原有岗位且客户空绑定被拒绝() {
        assertThat(guard.allowedPermissionsFor(RoleCode.OPERATOR)).doesNotContain(
                PermissionCode.SAMPLING_MAINTAIN, PermissionCode.GOLDWORK_MAINTAIN, PermissionCode.ASSET_CUSTODY,
                PermissionCode.OUTSOURCING_VIEW, PermissionCode.OUTSOURCING_MAINTAIN);
        assertThat(guard.allowedPermissionsFor(RoleCode.RECEIVER)).doesNotContain(PermissionCode.SAMPLING_VIEW);
        assertThat(guard.allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).doesNotContain(PermissionCode.ASSET_VIEW);
        assertThat(guard.allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).doesNotContain(PermissionCode.GOLDWORK_VIEW);
        assertThat(guard.allowedPermissionsFor(RoleCode.GENERAL_MANAGER)).doesNotContain(
                PermissionCode.SAMPLING_MAINTAIN, PermissionCode.GOLDWORK_MAINTAIN, PermissionCode.ASSET_CUSTODY);
        assertThat(guard.allowedPermissionsFor(RoleCode.BUSINESS_MANAGER)).doesNotContain(PermissionCode.SAMPLING_MAINTAIN);

        OperatorContext unbound = new OperatorContext(
                "客户账号", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of());
        assertThatThrownBy(() -> RefactorPermissionContract.assertCustomerRead(unbound, 8L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("没有绑定客户");

        OperatorContext bound = new OperatorContext(
                "客户账号", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "", List.of("8"));
        RefactorPermissionContract.assertCustomerRead(bound, 8L);
        assertThatThrownBy(() -> RefactorPermissionContract.assertAction(guard, PermissionCode.SAMPLING_MAINTAIN, bound))
                .isInstanceOf(PermissionDeniedException.class);

        OperatorContext noCraft = new OperatorContext(
                "业务管理", RoleCode.BUSINESS_MANAGER, DataScope.ALL, false, "", List.of(), List.of());
        assertThatThrownBy(() -> RefactorPermissionContract.assertAction(guard, PermissionCode.BUSINESS_PRODUCTION_REPORT, noCraft))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("缺少顶岗");
    }
}
