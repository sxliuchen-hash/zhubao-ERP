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
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
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
        assertThat(session.passwordChangeRequired()).isTrue();
        AuthSession changed = fixture.sessionService().changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("Secure-2026-Plan!", "Secure-2026-New!")
        );
        OperatorContext context = fixture.sessionService().operatorContext("Bearer " + changed.token()).orElseThrow();

        assertThat(changed.customerScope()).containsExactly("恒信珠宝", "雅辰饰品");
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
        assertThat(account.passwordChangeRequired()).isTrue();
        assertThat(fixture.managementService().accounts())
                .filteredOn(summary -> summary.username().equals("planner"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.passwordChangeRequired()).isTrue());
        assertThat(fixture.auditRepository().findRecent(20))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("更新账号并重置密码", "passwordChangeRequired=true"));
        assertThat(fixture.approvalRepository().findAll())
                .anySatisfy(task -> assertThat(task.sourceSummary())
                        .contains("action=更新账号并重置密码", "passwordChangeRequired=true"));
        assertThatThrownBy(() -> fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Old!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-New!")))
                .satisfies(session -> {
                    assertThat(session.username()).isEqualTo("planner");
                    assertThat(session.passwordChangeRequired()).isTrue();
                });
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
                    assertThat(item.missingCapabilities()).contains("全局强制申请单号", "审批通过态强制校验");
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
                    assertThat(item.enforcementStatus()).isEqualTo("申请单号和审批通过态强制校验已开启");
                    assertThat(item.missingCapabilities()).doesNotContain("全局强制申请单号");
                    assertThat(item.missingCapabilities()).doesNotContain("审批通过态强制校验");
                    assertThat(item.missingCapabilities()).contains("正式电子签字");
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
        assertThat(draft.missingCapabilities()).contains("全局强制申请单号", "审批通过态强制校验");
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
    void preApprovalApplicationPersistsDraftEvaluationAndCreatesHandoffTask() {
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
                "not yet an enforcing IAM workflow",
                "approvalTaskCode=AUTH-PRE-TASK-"
        );
        assertThat(application.approvalTaskCode()).startsWith("AUTH-PRE-TASK-");
        assertThat(fixture.managementService().preApprovalApplications())
                .extracting(AuthAccountPreApprovalApplication::applicationCode)
                .containsExactly(application.applicationCode());
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.approvalRepository().findAll())
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.approvalCode()).isEqualTo(application.approvalTaskCode());
                    assertThat(task.scene()).isEqualTo("ACCOUNT_PRE_APPROVAL");
                    assertThat(task.businessCode()).isEqualTo(application.applicationCode());
                    assertThat(task.status()).isEqualTo(ApprovalStatus.WAITING_APPROVAL);
                    assertThat(task.sourceStatus()).isEqualTo("WAITING_EVIDENCE");
                    assertThat(task.evidenceCode()).isEqualTo(application.applicationCode());
                    assertThat(task.sourceSummary()).contains(
                            "applicationCode=" + application.applicationCode(),
                            "targetUsername=planner",
                            "missingEvidence=",
                            "not final IAM approval"
                    );
                });
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
                "planner",
                "AUTH-PRE-TASK-APPROVED-001"
        );
        fixture.preApprovalApplicationRepository().save(application);
        fixture.approvalRepository().save(preApprovalTask(
                application.approvalTaskCode(),
                application.applicationCode(),
                ApprovalStatus.APPROVED
        ));

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
    void enforcingPreApprovalApplicationCodeRejectsWaitingApprovalTask() {
        Fixture fixture = fixture(true);
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-READY-WAITING-TASK-001",
                "AUTH-PRE-CREATE-001",
                "创建生产账号",
                "planner",
                "AUTH-PRE-TASK-WAITING-001"
        );
        fixture.preApprovalApplicationRepository().save(application);
        fixture.approvalRepository().save(preApprovalTask(
                application.approvalTaskCode(),
                application.applicationCode(),
                ApprovalStatus.WAITING_APPROVAL
        ));

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
                .hasMessageContaining("Account pre-approval task is not approved")
                .hasMessageContaining("WAITING_APPROVAL");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.auditRepository().findRecent(10)).isEmpty();
    }

    @Test
    void enforcingPreApprovalApplicationCodeRejectsApplicationWithoutApprovalTask() {
        Fixture fixture = fixture(true);
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-READY-NO-TASK-001",
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
                .hasMessageContaining("has no approval task");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.auditRepository().findRecent(10)).isEmpty();
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
        assertThat(fixture.auditRepository().findRecent(20))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("停用账号", "revokedSessions=0"));
        assertThat(fixture.approvalRepository().findAll())
                .anySatisfy(task -> assertThat(task.sourceSummary())
                        .contains("action=停用账号", "revokedSessions=0"));
    }

    @Test
    void disablingAccountRevokesExistingBearerSessions() {
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
        AuthSession first = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));
        AuthSession second = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));
        AuthSession leader = fixture.sessionService().login(new AuthLoginCommand("leader", "leader123"));

        fixture.managementService().updateEnabled("planner", false, "离职交接", admin);

        assertThatThrownBy(() -> fixture.sessionService().session("Bearer " + first.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话不存在或已退出");
        assertThatThrownBy(() -> fixture.sessionService().session("Bearer " + second.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话不存在或已退出");
        assertThat(fixture.sessionService().session("Bearer " + leader.token())).isPresent();
        assertThat(fixture.auditRepository().findRecent(20))
                .filteredOn(event -> event.eventType().equals("SESSION_REVOKED"))
                .hasSize(2)
                .allSatisfy(event -> {
                    assertThat(event.username()).isEqualTo("planner");
                    assertThat(event.reason()).contains("Account disabled by", "account=planner");
                    assertThat(event.sessionTokenSuffix()).isNotEmpty();
                });
        assertThat(fixture.auditRepository().findRecent(20))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("停用账号", "revokedSessions=2"));
        assertThat(fixture.approvalRepository().findAll())
                .anySatisfy(task -> assertThat(task.sourceSummary())
                        .contains("action=停用账号", "revokedSessions=2"));
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
        return readyPreApprovalApplication(applicationCode, readinessCode, actionType, targetUsername, "");
    }

    private AuthAccountPreApprovalApplication readyPreApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername,
            String approvalTaskCode
    ) {
        return preApprovalApplication(
                applicationCode,
                readinessCode,
                actionType,
                targetUsername,
                List.of(),
                List.of(),
                "READY_FOR_BLOCKING_APPROVAL",
                true,
                approvalTaskCode
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
                List.of("审批通过态强制校验"),
                "WAITING_EVIDENCE",
                false,
                ""
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
            boolean readyForBlockingApproval,
            String approvalTaskCode
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
                approvalTaskCode,
                "2026-07-09 10:00:00"
        );
    }

    private ApprovalTask preApprovalTask(String approvalCode, String applicationCode, ApprovalStatus status) {
        return new ApprovalTask(
                approvalCode,
                "ACCOUNT_PRE_APPROVAL",
                applicationCode,
                "Account pre-approval review / planner",
                status,
                "system-admin",
                "GENERAL_MANAGER|ADMIN",
                "GENERAL_MANAGER",
                status == ApprovalStatus.APPROVED ? "green" : "amber",
                "READY_FOR_BLOCKING_APPROVAL",
                "applicationCode=" + applicationCode,
                applicationCode,
                false,
                "2026-07-09T10:00:00+08:00",
                status == ApprovalStatus.APPROVED ? "2026-07-09T10:10:00+08:00" : null,
                status == ApprovalStatus.APPROVED ? "general-manager" : null,
                status == ApprovalStatus.APPROVED ? "approved for account action" : null,
                status == ApprovalStatus.APPROVED
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
        AuthSessionService sessionService = new AuthSessionService(
                navigationMenuService,
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
        AuthUserAccountManagementService managementService = new AuthUserAccountManagementService(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                auditRepository,
                approvalService,
                preApprovalApplicationRepository,
                sessionService,
                enforcePreApprovalForAccountActions
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
