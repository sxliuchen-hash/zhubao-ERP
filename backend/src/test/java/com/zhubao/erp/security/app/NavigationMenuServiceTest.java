package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationMenuServiceTest {

    private final NavigationMenuService service = new NavigationMenuService(new InMemoryNavigationMenuRepository());

    @Test
    void returnsRoleScopedMenuTreeAndButtonPermissions() {
        assertThat(service.menuCodesFor(RoleCode.WORKSHOP_LEADER))
                .contains("dashboard", "production", "quality", "approvals", "sample", "operations")
                .doesNotContain("configuration");

        assertThat(service.menuTreeFor(RoleCode.WORKSHOP_LEADER))
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("sample");
                    assertThat(item.buttonCodes()).contains("REGISTER_IMPORT_REVIEW", "REGISTER_IMPORT_POSTING");
                });
    }

    @Test
    void keepsOperatorMenuNarrowForShopFloorUse() {
        assertThat(service.menuCodesFor(RoleCode.OPERATOR))
                .containsExactly("dashboard", "production", "mobile");
    }

    @Test
    void exposesAuthAccountManagementButtonOnUsersMenu() {
        assertThat(service.menuTreeFor(RoleCode.ADMIN))
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("users");
                    assertThat(item.buttonCodes()).contains("AUTH_ACCOUNT_MANAGE");
                });
    }

    @Test
    void 用户管理仅系统管理员可见() {
        assertThat(service.menuCodesFor(RoleCode.ADMIN)).contains("users");
        for (RoleCode role : RoleCode.values()) {
            if (role != RoleCode.ADMIN) assertThat(service.menuCodesFor(role)).doesNotContain("users");
        }
    }

    @Test
    void showsFinanceMenuOnlyToFinanceAndManagers() {
        assertThat(service.menuCodesFor(RoleCode.FINANCE_STATISTICIAN))
                .contains("dashboard", "approvals", "finance")
                .doesNotContain("production", "quality");
        assertThat(service.menuTreeFor(RoleCode.ADMIN))
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("finance");
                    assertThat(item.buttonCodes()).contains("FINANCE_BILLING_POST", "FINANCE_PAYROLL");
                });
        assertThat(service.menuCodesFor(RoleCode.OPERATOR)).doesNotContain("finance");
    }

    @Test
    void showsCustomerArchiveToOfficeRolesAndNotOperators() {
        assertThat(service.menuCodesFor(RoleCode.ADMIN)).contains("customers");
        assertThat(service.menuCodesFor(RoleCode.RECEIVER)).contains("customers");
        assertThat(service.menuCodesFor(RoleCode.FINANCE_STATISTICIAN)).contains("customers");
        assertThat(service.menuCodesFor(RoleCode.BUSINESS_MANAGER)).contains("customers");
        assertThat(service.menuCodesFor(RoleCode.OPERATOR)).doesNotContain("customers");
        assertThat(service.menuCodesFor(RoleCode.QUALITY_INSPECTOR)).doesNotContain("customers");
    }

    @Test
    void showsLabelMenuToInboundRolesAndNotOperators() {
        assertThat(service.menuCodesFor(RoleCode.ADMIN)).contains("label");
        assertThat(service.menuCodesFor(RoleCode.RECEIVER)).contains("label");
        assertThat(service.menuCodesFor(RoleCode.OPERATOR)).doesNotContain("label");
    }
}
