package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
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
import com.zhubao.erp.security.infra.InMemoryAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthControllerAccountManagementTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    @TempDir
    Path tempDir;

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
        InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        InMemoryDocumentManagementRepository documentRepository = new InMemoryDocumentManagementRepository();
        DocumentManagementService documentManagementService = new DocumentManagementService(
                documentRepository,
                localStorage()
        );
        AuthSessionService authSessionService = new AuthSessionService(
                new NavigationMenuService(new com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository()),
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
        AuthUserAccountManagementService accountManagementService = new AuthUserAccountManagementService(accountRepository, passwordHasher, passwordPolicy, passwordExpiryPolicy, auditRepository, preApprovalApplicationRepository, null, documentRepository, null, null);
        OperatorContextResolver operatorContextResolver = new OperatorContextResolver(authSessionService, true);
        AuthController controller = new AuthController(
                authSessionService,
                accountManagementService,
                operatorContextResolver,
                new PermissionGuard()
        );
        return new Fixture(controller, accountRepository, preApprovalApplicationRepository, documentRepository);
    }

    private LocalAttachmentObjectStorage localStorage() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        return new LocalAttachmentObjectStorage(properties);
    }

    @Test
    void 管理员可以读取权限目录并删除账号() {
        Fixture fixture = fixture();
        var options = fixture.controller().accountOptions(request(RoleCode.ADMIN)).data();
        assertThat(options.roles()).extracting(item -> item.value()).containsExactly(
                "ADMIN", "GENERAL_MANAGER", "BUSINESS_MANAGER", "SALES", "RECEIVER", "WORKSHOP_LEADER", "OPERATOR",
                "QUALITY_INSPECTOR", "WAREHOUSE_KEEPER", "FINANCE_STATISTICIAN");
        assertThat(options.roles()).filteredOn(item -> item.value().equals("GENERAL_MANAGER"))
                .singleElement().satisfies(item -> assertThat(item.permissions()).extracting(permission -> permission.code())
                        .doesNotContain("AUTH_ACCOUNT_MANAGE", "CUSTOMER_AUTHORIZATION_MANAGE"));
        assertThat(fixture.controller().deleteAccount("operator", request(RoleCode.ADMIN)).data()).isNull();
        assertThat(fixture.accountRepository().findByUsername("operator")).isEmpty();
    }

    @Test
    void 总经理不可读取用户列表目录或删除账号() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.controller().accounts(request(RoleCode.GENERAL_MANAGER)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.controller().accountOptions(request(RoleCode.GENERAL_MANAGER)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.controller().deleteAccount("operator", request(RoleCode.GENERAL_MANAGER)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private MockHttpServletRequest request(RoleCode role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(OperatorContextResolver.OPERATOR_HEADER, role == RoleCode.ADMIN ? "系统管理员" : "操作工 王强");
        request.addHeader(OperatorContextResolver.ROLE_HEADER, role.name());
        request.addHeader(OperatorContextResolver.DATA_SCOPE_HEADER, DataScope.ALL.name());
        return request;
    }

    private AuthAccountPreApprovalApplication readyPreApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername
    ) {
        return new AuthAccountPreApprovalApplication(
                applicationCode,
                "AUTH-PRE-DRAFT-" + readinessCode + "-" + targetUsername.toUpperCase(),
                readinessCode,
                actionType,
                targetUsername,
                "系统管理员",
                "ADMIN",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                false,
                false,
                "账号动作前置审批放行校验",
                List.of("账号申请单", "直属负责人审批", "系统管理员复核"),
                List.of("账号申请单：AUTH-REQ-READY", "直属负责人审批", "系统管理员复核"),
                List.of(),
                List.of(),
                List.of("GENERAL_MANAGER", "ADMIN"),
                "可进入阻断式审批",
                "READY_FOR_BLOCKING_APPROVAL",
                true,
                "applicationBoundary=validated pre-approval application for account action",
                "允许账号动作进入放行校验",
                "中",
                "green",
                "",
                "2026-07-09 10:00:00",
                List.of(),
                "DISABLE"
        );
    }

    private record Fixture(
            AuthController controller,
            InMemoryAuthUserAccountRepository accountRepository,
            InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            InMemoryDocumentManagementRepository documentRepository
    ) {
    }
}
