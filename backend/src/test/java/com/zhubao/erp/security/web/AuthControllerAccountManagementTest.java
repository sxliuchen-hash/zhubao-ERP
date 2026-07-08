package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalReadiness;
import com.zhubao.erp.security.domain.AuthAuditIntegritySummary;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.AuthUserAccountManagementService;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.app.PasswordExpiryPolicy;
import com.zhubao.erp.security.app.PasswordPolicy;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccountStatusCommand;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.AuthUserAccountUnlockCommand;
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
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
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();

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
    void preApprovalReadinessRequiresManagementPermission() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.OPERATOR);

        assertThatThrownBy(() -> fixture.controller().accountPreApprovalReadiness(request))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("OPERATOR")
                .hasMessageContaining("登录账号管理");
    }

    @Test
    void preApprovalDraftRequiresManagementPermission() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.OPERATOR);

        assertThatThrownBy(() -> fixture.controller().draftAccountPreApproval(preApprovalDraftCommand(), request))
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
    void exposesPreApprovalReadinessThroughControllerBoundary() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.ADMIN);

        ApiResponse<List<AuthAccountPreApprovalReadiness>> readiness =
                fixture.controller().accountPreApprovalReadiness(request);

        assertThat(readiness.data())
                .extracting(AuthAccountPreApprovalReadiness::readinessCode)
                .contains("AUTH-PRE-CREATE-001", "AUTH-PRE-ROLE-001");
        assertThat(readiness.data())
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-CREATE-001"))
                .singleElement()
                .satisfies(item -> assertThat(item.missingCapabilities())
                        .contains("前置申请单号", "阻断式审批状态"));
    }

    @Test
    void evaluatesPreApprovalDraftThroughControllerBoundary() {
        Fixture fixture = fixture();
        MockHttpServletRequest request = request(RoleCode.ADMIN);

        ApiResponse<AuthAccountPreApprovalDraft> draft =
                fixture.controller().draftAccountPreApproval(preApprovalDraftCommand(), request);

        assertThat(draft.data().draftCode()).isEqualTo("AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER");
        assertThat(draft.data().blockingStatus()).isEqualTo("缺少前置材料");
        assertThat(draft.data().missingEvidence()).contains("直属负责人审批");
        assertThat(draft.data().missingCapabilities()).contains("阻断式审批状态");
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

    @Test
    void changesOwnPasswordThroughBearerOnlyBoundary() {
        Fixture fixture = fixture();
        AuthSession session = fixture.controller().login(new AuthLoginCommand("leader", "leader123")).data();
        MockHttpServletRequest bearerRequest = new MockHttpServletRequest();
        bearerRequest.addHeader(AuthSessionService.AUTHORIZATION_HEADER, "Bearer " + session.token());

        ApiResponse<AuthSession> changed = fixture.controller().changeOwnPassword(
                new ChangeOwnPasswordCommand("leader123", "Secure-2026-Leader!"),
                bearerRequest
        );

        assertThat(changed.data().token()).isEqualTo(session.token());
        assertThat(changed.data().passwordExpired()).isFalse();
        assertThatThrownBy(() -> fixture.controller().login(new AuthLoginCommand("leader", "leader123")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(fixture.controller().login(new AuthLoginCommand("leader", "Secure-2026-Leader!")).data().username())
                .isEqualTo("leader");
    }

    @Test
    void ownPasswordChangeDoesNotAcceptReviewRoleHeaderFallback() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller().changeOwnPassword(
                new ChangeOwnPasswordCommand("leader123", "Secure-2026-Leader!"),
                request(RoleCode.ADMIN)
        ))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("自助改密需要登录会话");
    }

    @Test
    void exposesAuthAuditIntegritySummary() {
        Fixture fixture = fixture();
        fixture.controller().login(new AuthLoginCommand("leader", "leader123"));

        ApiResponse<AuthAuditIntegritySummary> summary = fixture.controller().auditIntegrity();

        assertThat(summary.data().verified()).isTrue();
        assertThat(summary.data().checkedEvents()).isGreaterThanOrEqualTo(1);
        assertThat(summary.data().latestEventHash()).hasSize(64);
    }

    private Fixture fixture() {
        InMemoryAuthAuditRepository auditRepository = new InMemoryAuthAuditRepository();
        InMemoryAuthUserAccountRepository accountRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService authSessionService = new AuthSessionService(
                new NavigationMenuService(),
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
        AuthUserAccountManagementService accountManagementService = new AuthUserAccountManagementService(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                auditRepository
        );
        OperatorContextResolver operatorContextResolver = new OperatorContextResolver(authSessionService, true);
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

    private AuthAccountPreApprovalDraftCommand preApprovalDraftCommand() {
        return new AuthAccountPreApprovalDraftCommand(
                "CREATE_ACCOUNT",
                "planner",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false,
                "新增业务账号",
                List.of("账号申请单：AUTH-REQ-001")
        );
    }

    private record Fixture(
            AuthController controller,
            InMemoryAuthUserAccountRepository accountRepository
    ) {
    }
}
