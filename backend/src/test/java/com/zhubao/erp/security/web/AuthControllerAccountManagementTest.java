package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.AuthUserAccountManagementService;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.app.PasswordPolicy;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccountStatusCommand;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.AuthUserAccountUnlockCommand;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthControllerAccountManagementTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void accountListRequiresManagementPermission() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.OPERATOR);

        assertThatThrownBy(() -> fixture.controller().accounts(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("OPERATOR")
                .hasMessageContaining("登录账号管理");
    }

    @Test
    void accountListDoesNotUseAnonymousAdminFallback() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller().accounts(new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话")
                .hasMessageContaining("显式评审角色");
    }

    @Test
    void managesAccountsThroughControllerBoundary() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.ADMIN);

        ApiResponse<AuthUserAccountSummary> created = fixture.controller().upsertAccount(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), request);
        fixture.accountRepository().recordLoginFailure("planner", 5, Instant.now().plusSeconds(900));
        ApiResponse<AuthUserAccountSummary> disabled = fixture.controller().updateAccountStatus(
                "planner",
                new AuthUserAccountStatusCommand(false, "离职交接"),
                request
        );
        ApiResponse<AuthUserAccountSummary> unlocked = fixture.controller().unlockAccount(
                "planner",
                new AuthUserAccountUnlockCommand("误锁恢复"),
                request
        );
        ApiResponse<List<AuthUserAccountSummary>> accounts = fixture.controller().accounts(request);

        assertThat(created.data().username()).isEqualTo("planner");
        assertThat(disabled.data().enabled()).isFalse();
        assertThat(unlocked.data().failedLoginCount()).isZero();
        assertThat(unlocked.data().lockedUntil()).isEmpty();
        assertThat(accounts.data())
                .extracting(AuthUserAccountSummary::username)
                .contains("planner");
    }

    private Fixture fixture() {
        InMemoryAuthAuditRepository auditRepository = new InMemoryAuthAuditRepository();
        InMemoryAuthUserAccountRepository accountRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService authSessionService = new AuthSessionService(
                new NavigationMenuService(),
                auditRepository,
                accountRepository,
                passwordHasher
        );
        AuthUserAccountManagementService accountManagementService = new AuthUserAccountManagementService(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                auditRepository
        );
        OperatorContextResolver operatorContextResolver = new OperatorContextResolver(authSessionService);
        AuthController controller = new AuthController(
                authSessionService,
                accountManagementService,
                operatorContextResolver,
                new PermissionGuard()
        );
        return new Fixture(controller, accountRepository);
    }

    private MockHttpServletRequest request(RoleCode role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(OperatorContextResolver.OPERATOR_HEADER, role == RoleCode.ADMIN ? "系统管理员" : "操作工 王强");
        request.addHeader(OperatorContextResolver.ROLE_HEADER, role.name());
        request.addHeader(OperatorContextResolver.DATA_SCOPE_HEADER, DataScope.ALL.name());
        return request;
    }

    private record Fixture(
            AuthController controller,
            InMemoryAuthUserAccountRepository accountRepository
    ) {
    }
}
