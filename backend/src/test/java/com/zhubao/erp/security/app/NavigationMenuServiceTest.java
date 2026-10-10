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
    void exposesAuthAccountManagementButtonOnConfigurationMenu() {
        assertThat(service.menuTreeFor(RoleCode.ADMIN))
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("configuration");
                    assertThat(item.buttonCodes()).contains("AUTH_ACCOUNT_MANAGE");
                });
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
}
