package com.zhubao.erp.security.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalReadiness;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserAccountManagementServiceTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    private final NavigationMenuService navigationMenuService = new NavigationMenuService();
    private final OperatorContext admin = new OperatorContext(
            "系统管理员",
            RoleCode.ADMIN,
            DataScope.ALL,
            false,
            "admin-token-abcdef"
    );

    @Test
    void createsProductionAccountThatCanLogin() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "Planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        AuthSession session = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));

        assertThat(session.username()).isEqualTo("planner");
        assertThat(session.operator()).isEqualTo("计划员 陈敏");
        assertThat(session.role()).isEqualTo("SALES");
        assertThat(session.demoAccount()).isFalse();
    }

    @Test
    void createsCustomerScopedAccountForBearerDataScope() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "Planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝", "雅辰饰品", "恒信珠宝"),
                true,
                false
        ), admin);

        AuthSession session = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));
        OperatorContext context = fixture.sessionService().operatorContext("Bearer " + session.token()).orElseThrow();

        assertThat(session.customerScope()).containsExactly("恒信珠宝", "雅辰饰品");
        assertThat(context.customerScope()).containsExactly("恒信珠宝", "雅辰饰品");
        assertThat(fixture.managementService().accounts())
                .filteredOn(account -> account.username().equals("planner"))
                .singleElement()
                .satisfies(account -> assertThat(account.customerScope()).containsExactly("恒信珠宝", "雅辰饰品"));
    }

    @Test
    void updatesAccountWithoutPasswordKeepsExistingPasswordHash() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        String originalHash = fixture.accountRepository().findByUsername("planner").orElseThrow().passwordHash();
        Instant originalPasswordChangedAt = fixture.accountRepository().findByUsername("planner").orElseThrow().passwordChangedAt();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                null,
                "计划员 陈敏-调整",
                "WAREHOUSE_KEEPER",
                "DEPARTMENT",
                true,
                false
        ), admin);

        AuthUserAccount updated = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(updated.passwordHash()).isEqualTo(originalHash);
        assertThat(updated.passwordChangedAt()).isEqualTo(originalPasswordChangedAt);
        assertThat(updated.operator()).isEqualTo("计划员 陈敏-调整");
        assertThat(updated.role()).isEqualTo(RoleCode.WAREHOUSE_KEEPER);
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")).role())
                .isEqualTo("WAREHOUSE_KEEPER");
    }

    @Test
    void resettingPasswordClearsFailedLoginLockout() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Old!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        Instant oldPasswordChangedAt = Instant.now().minus(Duration.ofDays(91));
        AuthUserAccount oldAccount = fixture.accountRepository().findByUsername("planner").orElseThrow();
        fixture.accountRepository().save(new AuthUserAccount(
                oldAccount.username(),
                oldAccount.passwordHash(),
                oldAccount.operator(),
                oldAccount.role(),
                oldAccount.dataScope(),
                oldAccount.enabled(),
                oldAccount.demoAccount(),
                oldPasswordChangedAt,
                oldAccount.failedLoginCount(),
                oldAccount.lockedUntil()
        ));
        fixture.accountRepository().recordLoginFailure("planner", 5, Instant.now().plusSeconds(900));

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-New!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        AuthUserAccount account = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockedUntil()).isNull();
        assertThat(account.passwordChangedAt()).isAfter(oldPasswordChangedAt);
        assertThatThrownBy(() -> fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Old!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-New!")).username())
                .isEqualTo("planner");
    }

    @Test
    void accountSummaryExposesPasswordExpiryState() {
        Fixture fixture = fixture();
        Instant oldPasswordChangedAt = Instant.now().minus(Duration.ofDays(91));
        fixture.accountRepository().save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("Secure-2026-Old!"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                oldPasswordChangedAt,
                0,
                null
        ));

        assertThat(fixture.managementService().accounts())
                .filteredOn(account -> account.username().equals("planner"))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.passwordChangedAt()).isNotBlank();
                    assertThat(account.passwordExpiresAt()).isNotBlank();
                    assertThat(account.passwordExpired()).isTrue();
                });
    }

    @Test
    void preApprovalReadinessExposesMissingBlockingApprovalCapabilities() {
        Fixture fixture = fixture();

        List<AuthAccountPreApprovalReadiness> readiness = fixture.managementService().preApprovalReadiness();

        assertThat(readiness).hasSize(4);
        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-CREATE-001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.tone()).isEqualTo("red");
                    assertThat(item.riskLevel()).isEqualTo("高");
                    assertThat(item.enforcementStatus()).isEqualTo("申请单号可校验，默认不强制");
                    assertThat(item.missingCapabilities()).contains("全局强制申请单号", "阻断式审批状态");
                });
        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-ROLE-001"))
                .singleElement()
                .satisfies(item -> assertThat(item.missingCapabilities()).contains("客户授权表"));
    }

    @Test
    void preApprovalReadinessReflectsEnforcedApplicationCodeSwitch() {
        Fixture fixture = fixture(true);

        List<AuthAccountPreApprovalReadiness> readiness = fixture.managementService().preApprovalReadiness();

        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-CREATE-001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.enforcementStatus()).isEqualTo("申请单号强制校验已开启");
                    assertThat(item.missingCapabilities()).doesNotContain("全局强制申请单号");
                    assertThat(item.missingCapabilities()).contains("阻断式审批状态", "正式电子签字");
                });
    }

    @Test
    void preApprovalDraftEvaluatesMissingEvidenceWithoutWritingApprovalTask() {
        Fixture fixture = fixture();

        AuthAccountPreApprovalDraft draft = fixture.managementService().preApprovalDraft(new AuthAccountPreApprovalDraftCommand(
                "CREATE_ACCOUNT",
                "Planner",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝", "雅辰饰品"),
                true,
                false,
                "新增业务账号",
                List.of("账号申请单：AUTH-REQ-001")
        ), admin);

        assertThat(draft.draftCode()).isEqualTo("AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER");
        assertThat(draft.readinessCode()).isEqualTo("AUTH-PRE-CREATE-001");
        assertThat(draft.blockingStatus()).isEqualTo("缺少前置材料");
        assertThat(draft.readyForBlockingApproval()).isFalse();
        assertThat(draft.providedEvidence()).containsExactly("账号申请单：AUTH-REQ-001");
        assertThat(draft.missingEvidence()).contains("直属负责人审批", "系统管理员复核");
        assertThat(draft.missingCapabilities()).contains("全局强制申请单号", "阻断式审批状态");
        assertThat(draft.sourceSummary()).contains(
                "action=创建生产账号",
                "targetUsername=planner",
                "targetCustomerScope=恒信珠宝|雅辰饰品",
                "pre-action draft only"
        );
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
    }

    @Test
    void preApprovalDraftRejectsUnknownActionType() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.managementService().preApprovalDraft(new AuthAccountPreApprovalDraftCommand(
                "DELETE_ACCOUNT",
                "planner",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of(),
                true,
                false,
                "未知动作",
                List.of()
        ), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知账号前置审批动作");
    }

    @Test
    void preApprovalApplicationPersistsDraftEvaluationWithoutWritingAccountOrApprovalTask() {
        Fixture fixture = fixture();

        AuthAccountPreApprovalApplication application = fixture.managementService().submitPreApprovalApplication(
                new AuthAccountPreApprovalApplicationCommand(
                        "CREATE_ACCOUNT",
                        "Planner",
                        "计划员 陈敏",
                        "SALES",
                        "CUSTOMER_RANGE",
                        List.of("恒信珠宝", "雅辰饰品"),
                        true,
                        false,
                        "新增业务账号前置审批申请",
                        List.of("账号申请单：AUTH-REQ-001")
                ),
                admin
        );

        assertThat(application.applicationCode()).startsWith("AUTH-PRE-APP-");
        assertThat(application.draftCode()).isEqualTo("AUTH-PRE-DRAFT-CREATE_ACCOUNT-PLANNER");
        assertThat(application.applicationStatus()).isEqualTo("WAITING_EVIDENCE");
        assertThat(application.blockingStatus()).isEqualTo("缺少前置材料");
        assertThat(application.targetUsername()).isEqualTo("planner");
        assertThat(application.targetCustomerScope()).containsExactly("恒信珠宝", "雅辰饰品");
        assertThat(application.missingEvidence()).contains("直属负责人审批", "系统管理员复核");
        assertThat(application.sourceSummary()).contains(
                "applicationStatus=WAITING_EVIDENCE",
                "stored pre-approval application",
                "not yet an enforcing IAM workflow"
        );
        assertThat(fixture.managementService().preApprovalApplications())
                .extracting(AuthAccountPreApprovalApplication::applicationCode)
                .containsExactly(application.applicationCode());
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
    }

    @Test
    void bindsReadyPreApprovalApplicationToAccountCreationAuditAndSummary() {
        Fixture fixture = fixture();
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-READY-001",
                "AUTH-PRE-CREATE-001",
                "创建生产账号",
                "planner"
        );
        fixture.preApprovalApplicationRepository().save(application);

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false,
                application.applicationCode()
        ), admin);

        assertThat(fixture.accountRepository().findByUsername("planner")).isPresent();
        assertThat(fixture.auditRepository().findRecent(10))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("创建账号", "前置申请单：AUTH-PRE-APP-READY-001"));
        assertThat(fixture.approvalRepository().findAll())
                .singleElement()
                .satisfies(task -> assertThat(task.sourceSummary())
                        .contains("action=创建账号；前置申请单：AUTH-PRE-APP-READY-001"));
    }

    @Test
    void enforcingPreApprovalApplicationCodeRejectsAccountActionWithoutCode() {
        Fixture fixture = fixture(true);

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false
        ), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前置审批强制开关")
                .hasMessageContaining("AUTH-PRE-CREATE-001")
                .hasMessageContaining("planner");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.auditRepository().findRecent(10)).isEmpty();
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
    }

    @Test
    void enforcingPreApprovalApplicationCodeAllowsReadyApplication() {
        Fixture fixture = fixture(true);
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-READY-ENFORCED-001",
                "AUTH-PRE-CREATE-001",
                "创建生产账号",
                "planner"
        );
        fixture.preApprovalApplicationRepository().save(application);

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false,
                application.applicationCode()
        ), admin);

        assertThat(fixture.accountRepository().findByUsername("planner")).isPresent();
        assertThat(fixture.auditRepository().findRecent(10))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("前置申请单：AUTH-PRE-APP-READY-ENFORCED-001"));
    }

    @Test
    void rejectsPreApprovalApplicationThatIsNotReadyForAccountAction() {
        Fixture fixture = fixture();
        AuthAccountPreApprovalApplication application = waitingPreApprovalApplication(
                "AUTH-PRE-APP-WAITING-001",
                "AUTH-PRE-CREATE-001",
                "创建生产账号",
                "planner"
        );
        fixture.preApprovalApplicationRepository().save(application);

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false,
                application.applicationCode()
        ), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未具备账号动作放行条件");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
    }

    @Test
    void rejectsWeakPasswordThroughManagementBoundary() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "admin123",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("常见弱口令");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
    }

    @Test
    void disabledAccountCannotLoginAndUsesGenericMessage() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        fixture.managementService().updateEnabled("planner", false, "离职交接", admin);

        assertThat(fixture.accountRepository().findByUsername("planner")).get()
                .extracting(AuthUserAccount::enabled)
                .isEqualTo(false);
        assertThatThrownBy(() -> fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void adminUnlockClearsLockoutAndAllowsCorrectPasswordLogin() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        fixture.accountRepository().recordLoginFailure("planner", 5, Instant.now().plusSeconds(900));

        fixture.managementService().unlock("planner", "现场核实本人", admin);

        AuthUserAccount account = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockedUntil()).isNull();
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")).username())
                .isEqualTo("planner");
    }

    @Test
    void writesAccountManagementAuditEvents() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        fixture.managementService().updateEnabled("planner", false, "权限回收", admin);
        fixture.managementService().unlock("planner", "误锁恢复", admin);

        assertThat(fixture.auditRepository().findRecent(10))
                .extracting("eventType")
                .contains("ACCOUNT_MANAGEMENT");
        assertThat(fixture.auditRepository().findRecent(10))
                .anySatisfy(event -> {
                    assertThat(event.eventId()).startsWith("AUTH-ACC-");
                    assertThat(event.username()).isEqualTo("planner");
                    assertThat(event.operator()).isEqualTo("系统管理员");
                    assertThat(event.role()).isEqualTo("ADMIN");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.sessionTokenSuffix()).isEqualTo("abcdef");
                });
    }

    @Test
    void writesAccountManagementApprovalSummaries() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        fixture.managementService().updateEnabled("planner", false, "权限回收", admin);
        fixture.managementService().unlock("planner", "误锁恢复", admin);

        List<ApprovalTask> tasks = fixture.approvalRepository().findAll();
        assertThat(tasks).hasSize(3);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.approvalCode()).startsWith("AUTH-ACC-APP-");
            assertThat(task.scene()).isEqualTo("ACCOUNT_MANAGEMENT");
            assertThat(task.businessCode()).isEqualTo("planner");
            assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
            assertThat(task.applicant()).isEqualTo("系统管理员");
            assertThat(task.ownerRole()).isEqualTo("GENERAL_MANAGER");
            assertThat(task.formalPayroll()).isFalse();
            assertThat(task.locked()).isFalse();
            assertThat(task.sourceSummary()).contains(
                    "targetOperator=计划员 陈敏",
                    "targetRole=SALES",
                    "targetDataScope=CUSTOMER_RANGE",
                    "targetCustomerScope=",
                    "demoAccount=false",
                    "not a blocking IAM workflow"
            );
        });
        assertThat(tasks).anySatisfy(task -> assertThat(task.sourceSummary()).contains("action=创建账号"));
        assertThat(tasks).anySatisfy(task -> assertThat(task.sourceSummary()).contains("action=停用账号；原因：权限回收"));
        assertThat(tasks).anySatisfy(task -> assertThat(task.sourceSummary()).contains("action=管理员解锁账号；原因：误锁恢复"));
    }

    private AuthAccountPreApprovalApplication readyPreApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername
    ) {
        return preApprovalApplication(
                applicationCode,
                readinessCode,
                actionType,
                targetUsername,
                List.of(),
                List.of(),
                "READY_FOR_BLOCKING_APPROVAL",
                true
        );
    }

    private AuthAccountPreApprovalApplication waitingPreApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername
    ) {
        return preApprovalApplication(
                applicationCode,
                readinessCode,
                actionType,
                targetUsername,
                List.of("直属负责人审批"),
                List.of("阻断式审批状态"),
                "WAITING_EVIDENCE",
                false
        );
    }

    private AuthAccountPreApprovalApplication preApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername,
            List<String> missingEvidence,
            List<String> missingCapabilities,
            String applicationStatus,
            boolean readyForBlockingApproval
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
                true,
                false,
                "账号动作前置审批放行校验",
                List.of("账号申请单", "直属负责人审批", "系统管理员复核"),
                List.of("账号申请单：AUTH-REQ-READY", "直属负责人审批", "系统管理员复核"),
                missingEvidence,
                missingCapabilities,
                List.of("GENERAL_MANAGER", "ADMIN"),
                readyForBlockingApproval ? "可进入阻断式审批" : "缺少前置材料",
                applicationStatus,
                readyForBlockingApproval,
                "applicationBoundary=validated pre-approval application for account action",
                readyForBlockingApproval ? "允许账号动作进入放行校验" : "补齐前置材料",
                readyForBlockingApproval ? "中" : "高",
                readyForBlockingApproval ? "green" : "red",
                "2026-07-09 10:00:00"
        );
    }

    private Fixture fixture() {
        return fixture(false);
    }

    private Fixture fixture(boolean enforcePreApprovalForAccountActions) {
        InMemoryAuthAuditRepository auditRepository = new InMemoryAuthAuditRepository();
        InMemoryAuthUserAccountRepository accountRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        InMemoryApprovalTaskRepository approvalRepository = new InMemoryApprovalTaskRepository();
        InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        ApprovalService approvalService = new ApprovalService(approvalRepository);
        AuthUserAccountManagementService managementService = new AuthUserAccountManagementService(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                auditRepository,
                approvalService,
                preApprovalApplicationRepository,
                enforcePreApprovalForAccountActions
        );
        AuthSessionService sessionService = new AuthSessionService(
                navigationMenuService,
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
        return new Fixture(
                accountRepository,
                auditRepository,
                approvalRepository,
                preApprovalApplicationRepository,
                managementService,
                sessionService
        );
    }

    private record Fixture(
            AuthUserAccountRepository accountRepository,
            InMemoryAuthAuditRepository auditRepository,
            InMemoryApprovalTaskRepository approvalRepository,
            InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthUserAccountManagementService managementService,
            AuthSessionService sessionService
    ) {
    }
}
