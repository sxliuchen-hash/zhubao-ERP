package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationMenuServiceTest {

    private final NavigationMenuService service = new NavigationMenuService();

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
}
