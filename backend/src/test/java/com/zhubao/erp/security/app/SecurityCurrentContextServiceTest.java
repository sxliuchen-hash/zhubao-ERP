package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.domain.SecurityCurrentContext;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCurrentContextServiceTest {

    private final SecurityCurrentContextService service = new SecurityCurrentContextService(
            new PermissionGuard(),
            new NavigationMenuService(new InMemoryNavigationMenuRepository())
    );

    @Test
    void customerRangeBoundaryReferencesAuthorizationTable() {
        OperatorContext context = new OperatorContext(
                "sales-review", RoleCode.SALES, DataScope.CUSTOMER_RANGE, false, "token", List.of("CUST-1001"));

        SecurityCurrentContext current = service.build(context);

        assertThat(current.dataScope()).isEqualTo("CUSTOMER_RANGE");
        assertThat(current.dataBoundary()).contains("sys_customer_authorization");
        assertThat(current.customerScope()).containsExactly("CUST-1001");
    }

    @Test
    void sessionLimitationNotesReferenceAuthorizationTable() {
        OperatorContext context = new OperatorContext(
                "gm-review", RoleCode.GENERAL_MANAGER, DataScope.ALL, false, "token", List.of());

        SecurityCurrentContext current = service.build(context);

        assertThat(current.limitationNotes())
                .anySatisfy(note -> assertThat(note).contains("sys_customer_authorization"));
    }

    @Test
    void exposesMenuCodesForRole() {
        OperatorContext context = new OperatorContext("operator-review", RoleCode.OPERATOR, DataScope.SELF);

        SecurityCurrentContext current = service.build(context);

        assertThat(current.menuCodes()).containsExactly("dashboard", "production", "mobile");
    }
}
