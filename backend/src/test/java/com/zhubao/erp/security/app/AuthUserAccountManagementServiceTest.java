package com.zhubao.erp.security.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.AttachmentStorageProperties;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.document.infra.LocalAttachmentObjectStorage;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalEvidenceSummary;
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
import com.zhubao.erp.security.infra.InMemoryCustomerAuthorizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserAccountManagementServiceTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    private final NavigationMenuService navigationMenuService =
            new NavigationMenuService(new com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository());
    @TempDir
    Path tempDir;
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
        assertThat(session.allowedProcesses()).isEmpty();
    }

    @Test
    void bindsWorkshopProcessesOntoTheExistingAccount() {
        Fixture fixture = fixture();

        var created = fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "station1",
                "Secure-2026-Station!",
                "雕刻工 林海",
                "OPERATOR",
                "SELF",
                null,
                true,
                true,
                null,
                List.of("抛光", "雕刻", "雕刻")
        ), admin);

        assertThat(created.allowedProcesses()).containsExactly("雕刻", "抛光");
        AuthSession session = fixture.sessionService().login(new AuthLoginCommand("station1", "Secure-2026-Station!"));
        assertThat(session.allowedProcesses()).containsExactly("雕刻", "抛光");

        var kept = fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "station1",
                null,
                "雕刻工 林海",
                "OPERATOR",
                "SELF",
                true,
                true
        ), admin);
        assertThat(kept.allowedProcesses()).containsExactly("雕刻", "抛光");

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "station1",
                null,
                "雕刻工 林海",
                "OPERATOR",
                "SELF",
                null,
                true,
                true,
                null,
                List.of("焊接")
        ), admin)).isInstanceOf(BusinessException.class).hasMessageContaining("未知工序");

        var cleared = fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "station1",
                null,
                "雕刻工 林海",
                "OPERATOR",
                "SELF",
                null,
                true,
                true,
                null,
                List.of()
        ), admin);
        assertThat(cleared.allowedProcesses()).isEmpty();
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
    void 账号客户范围同步写入客户授权表() {
        Fixture fixture = fixture();

        // 新建 CUSTOMER_RANGE 账号时，账号客户范围应同步写入授权表 sys_customer_authorization
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "Planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝", "雅辰饰品"),
                true,
                false
        ), admin);

        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner"))
                .extracting(auth -> auth.customerCode())
                .containsExactlyInAnyOrder("恒信珠宝", "雅辰饰品");

        // 收敛客户范围（去掉雅辰饰品）后，授权表应同步撤销不再授权的客户
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                null,
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false
        ), admin);

        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner"))
                .extracting(auth -> auth.customerCode())
                .containsExactly("恒信珠宝");
    }

    @Test
    void 数据范围切换为非客户范围时清空授权表() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "Planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                List.of("恒信珠宝"),
                true,
                false
        ), admin);
        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner")).hasSize(1);

        // dataScope 改为 DEPARTMENT，非 CUSTOMER_RANGE，应清空该账号的客户授权
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                null,
                "计划员 陈敏",
                "SALES",
                "DEPARTMENT",
                List.of(),
                true,
                false
        ), admin);

        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner")).isEmpty();
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
        assertThat(account.temporaryPasswordConsumed()).isFalse();
        assertThat(fixture.managementService().accounts())
                .filteredOn(summary -> summary.username().equals("planner"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.passwordChangeRequired()).isTrue());
        assertThat(fixture.auditRepository().findRecent(20))
                .anySatisfy(event -> assertThat(event.reason())
                        .contains("更新账号并重置密码", "passwordChangeRequired=true"));
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
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
                    assertThat(item.enforcementStatus()).isEqualTo("管理员直接管理；显式申请单可校验");
                    assertThat(item.missingCapabilities()).contains("全局强制申请单号", "审批通过态强制校验");
                });
        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-ROLE-001"))
                .singleElement()
                .satisfies(item -> assertThat(item.missingCapabilities()).contains("客户授权表"));
        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-PASSWORD-001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.missingCapabilities()).containsExactly("本人核验附件");
                    assertThat(item.nextAction()).isEqualTo("补本人核验附件");
                    assertThat(item.auditEvidence()).contains("消费首次临时密码登录通道");
                });
    }

    @Test
    void preApprovalReadinessReflectsEnforcedApplicationCodeSwitch() {
        Fixture fixture = fixture(true);

        List<AuthAccountPreApprovalReadiness> readiness = fixture.managementService().preApprovalReadiness();

        assertThat(readiness)
                .filteredOn(item -> item.readinessCode().equals("AUTH-PRE-CREATE-001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.enforcementStatus()).isEqualTo("管理员直接管理；显式申请单校验审批通过态");
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
    void preApprovalEvidenceSummarizesIdentityVerificationAttachments() {
        Fixture fixture = fixture();
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-PASSWORD-001",
                "AUTH-PRE-PASSWORD-001",
                "管理员重置密码",
                "planner"
        );
        fixture.preApprovalApplicationRepository().save(application);

        assertThat(fixture.managementService().preApprovalEvidence())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.applicationCode()).isEqualTo("AUTH-PRE-APP-PASSWORD-001");
                    assertThat(summary.requiredEvidence()).isEqualTo("本人核验附件");
                    assertThat(summary.attachmentCount()).isZero();
                    assertThat(summary.missingEvidence()).isEqualTo(1);
                    assertThat(summary.status()).isEqualTo("待补本人核验附件");
                    assertThat(summary.nextAction()).contains("businessType=账号前置审批");
                });

        fixture.documentRepository().saveAttachment(new EvidenceAttachment(
                "ATT-AUTH-PRE-PASSWORD-001",
                "DOC-AUTH-PRE-PASSWORD-001",
                "账号前置审批",
                application.applicationCode(),
                "planner-identity-check.pdf",
                "application/pdf",
                "local://evidence/auth-pre-approval/AUTH-PRE-APP-PASSWORD-001/planner-identity-check.pdf",
                "audit long-term",
                "PC 后台上传",
                "已上传",
                "系统管理员",
                "sha256:identity-check",
                "2026-07-09 11:00",
                "无",
                "green",
                null
        ));

        assertThat(fixture.managementService().preApprovalEvidence())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.documentCode()).isEqualTo("DOC-AUTH-PRE-PASSWORD-001");
                    assertThat(summary.attachmentCount()).isEqualTo(1);
                    assertThat(summary.missingEvidence()).isZero();
                    assertThat(summary.attachmentCodes()).containsExactly("ATT-AUTH-PRE-PASSWORD-001");
                    assertThat(summary.status()).isEqualTo("已关联证据");
                    assertThat(summary.tone()).isEqualTo("green");
                });
    }

    @Test
    void uploadsPreApprovalEvidenceAndCreatesApplicationDocumentPackage() {
        Fixture fixture = fixture();
        AuthAccountPreApprovalApplication application = readyPreApprovalApplication(
                "AUTH-PRE-APP-PASSWORD-UPLOAD-001",
                "AUTH-PRE-PASSWORD-001",
                "管理员重置密码",
                "planner"
        );
        fixture.preApprovalApplicationRepository().save(application);

        AuthAccountPreApprovalEvidenceSummary summary = fixture.managementService().uploadPreApprovalEvidence(
                application.applicationCode(),
                "identity-check.pdf",
                "application/pdf",
                "identity check content".getBytes(),
                admin
        );

        assertThat(summary.applicationCode()).isEqualTo(application.applicationCode());
        assertThat(summary.documentCode()).isEqualTo("DOC-AUTH-PRE-APP-PASSWORD-UPLOAD-001");
        assertThat(summary.requiredEvidence()).isEqualTo("本人核验附件");
        assertThat(summary.attachmentCount()).isEqualTo(1);
        assertThat(summary.missingEvidence()).isZero();
        assertThat(summary.status()).isEqualTo("已关联证据");
        assertThat(summary.attachmentCodes()).singleElement().asString().startsWith("ATT-UP-");
        assertThat(fixture.documentRepository().packages())
                .filteredOn(documentPackage -> documentPackage.code().equals("DOC-AUTH-PRE-APP-PASSWORD-UPLOAD-001"))
                .singleElement()
                .satisfies(documentPackage -> {
                    assertThat(documentPackage.category()).isEqualTo("账号前置审批");
                    assertThat(documentPackage.businessCode()).isEqualTo(application.applicationCode());
                    assertThat(documentPackage.linkedEvidence()).isEqualTo(1);
                    assertThat(documentPackage.status()).isEqualTo("已关联证据");
                });
        assertThat(fixture.documentRepository().attachments())
                .filteredOn(attachment -> attachment.businessCode().equals(application.applicationCode()))
                .singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.businessType()).isEqualTo("账号前置审批");
                    assertThat(attachment.fileName()).isEqualTo("identity-check.pdf");
                    assertThat(attachment.storageLocation()).contains("DOC-AUTH-PRE-APP-PASSWORD-UPLOAD-001");
                    assertThat(attachment.checksum()).startsWith("sha256:");
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
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
    }

    @Test
    void 强制开关开启时管理员仍可直接管理且不生成审批任务() {
        Fixture fixture = fixture(true);
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-Plan!", "计划员 陈敏", "SALES", "CUSTOMER_RANGE",
                List.of("恒信珠宝"), true, false), admin);
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-New!", "计划员 陈敏", "RECEIVER", "DEPARTMENT", true, false), admin);
        fixture.managementService().updateEnabled("planner", false, "停用", admin);
        fixture.managementService().unlock("planner", "解锁", admin);
        fixture.managementService().updateEnabled("planner", true, "启用", admin);
        assertThat(fixture.accountRepository().findByUsername("planner").orElseThrow().enabled()).isTrue();
        assertThat(fixture.auditRepository().findRecent(20)).filteredOn(event -> "ACCOUNT_MANAGEMENT".equals(event.eventType())).hasSize(5);
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
        assertThat(fixture.preApprovalApplicationRepository().findAll()).isEmpty();
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
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
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
        AuthSession changed = fixture.sessionService().changeOwnPassword(
                "Bearer " + first.token(),
                new ChangeOwnPasswordCommand("Secure-2026-Plan!", "Secure-2026-Plan-New!")
        );
        AuthSession second = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan-New!"));
        AuthSession leader = fixture.sessionService().login(new AuthLoginCommand("leader", "leader123"));

        fixture.managementService().updateEnabled("planner", false, "离职交接", admin);

        assertThatThrownBy(() -> fixture.sessionService().session("Bearer " + changed.token()))
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
        assertThat(fixture.approvalRepository().findAll()).isEmpty();
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
    void 常规账号管理不再生成事后审批任务() {
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

        assertThat(fixture.approvalRepository().findAll()).isEmpty();
    }

    private AuthAccountPreApprovalApplication readyPreApprovalApplication(
            String applicationCode,
            String readinessCode,
            String actionType,
            String targetUsername
    ) {
        return readyPreApprovalApplication(applicationCode, readinessCode, actionType, targetUsername, "");
    }

    @Test
    void completeEvidenceEnablesRealBlockingApprovalAndBindsTheTargetSnapshot() {
        Fixture fixture = fixture(true);
        List<String> evidence = fixture.managementService().preApprovalReadiness().stream()
                .filter(item -> item.readinessCode().equals("AUTH-PRE-CREATE-001"))
                .findFirst().orElseThrow().requiredBeforeAction();
        AuthAccountPreApprovalApplication application = fixture.managementService().submitPreApprovalApplication(
                new AuthAccountPreApprovalApplicationCommand("CREATE_ACCOUNT", "new-worker", "新操作员", "OPERATOR", "SELF", List.of(),
                        true, false, "新增岗位账号", evidence, List.of("抛光"), ""), admin);
        assertThat(application.readyForBlockingApproval()).isTrue();
        assertThat(application.applicationStatus()).isEqualTo("READY_FOR_BLOCKING_APPROVAL");
        assertThat(application.missingCapabilities()).contains("正式电子签字");
        approve(fixture, application);

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "new-worker", "Secure-2026-Worker!", "新操作员", "ADMIN", "ALL", List.of(), true, false,
                application.applicationCode(), List.of("抛光")), admin)).hasMessageContaining("目标资料、权限或工序不一致");
        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "new-worker", "Secure-2026-Worker!", "新操作员", "OPERATOR", "SELF", List.of(), true, false,
                application.applicationCode(), List.of("雕刻")), admin)).hasMessageContaining("目标资料、权限或工序不一致");
        assertThat(fixture.accountRepository().findByUsername("new-worker")).isEmpty();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "new-worker", "Secure-2026-Worker!", "新操作员", "OPERATOR", "SELF", List.of(), true, false,
                application.applicationCode(), List.of("抛光")), admin);
        assertThat(fixture.accountRepository().findByUsername("new-worker")).get()
                .satisfies(account -> assertThat(account.allowedProcesses()).containsExactly("抛光"));
        assertThat(fixture.preApprovalApplicationRepository().findByCode(application.applicationCode())).get()
                .satisfies(executed -> {
                    assertThat(executed.applicationStatus()).isEqualTo("EXECUTED");
                    assertThat(executed.readyForBlockingApproval()).isFalse();
                });
    }

    @Test
    void approvedDisableCannotBeUsedToUnlockEnableOrExecuteTwice() {
        Fixture fixture = fixture(true);
        List<String> evidence = fixture.managementService().preApprovalReadiness().stream()
                .filter(item -> item.readinessCode().equals("AUTH-PRE-STATUS-001"))
                .findFirst().orElseThrow().requiredBeforeAction();
        AuthAccountPreApprovalApplication application = fixture.managementService().submitPreApprovalApplication(
                new AuthAccountPreApprovalApplicationCommand("CHANGE_STATUS", "operator", "", "", "", List.of(),
                        false, null, "岗位交接停用", evidence, List.of(), "DISABLE"), admin);
        approve(fixture, application);
        assertThatThrownBy(() -> fixture.managementService().unlock("operator", "试图复用", application.applicationCode(), admin))
                .hasMessageContaining("启停或解锁动作与已批准申请不一致");
        assertThatThrownBy(() -> fixture.managementService().updateEnabled("operator", true, "试图复用", application.applicationCode(), admin))
                .hasMessageContaining("启停或解锁动作与已批准申请不一致");
        assertThat(fixture.accountRepository().findByUsername("operator")).get().satisfies(account -> assertThat(account.enabled()).isTrue());
        fixture.managementService().updateEnabled("operator", false, "岗位交接停用", application.applicationCode(), admin);
        assertThatThrownBy(() -> fixture.managementService().updateEnabled("operator", false, "重复停用", application.applicationCode(), admin))
                .hasMessageContaining("尚未具备账号动作放行条件");
    }

    private void approve(Fixture fixture, AuthAccountPreApprovalApplication application) {
        new ApprovalService(fixture.approvalRepository()).decide(application.approvalTaskCode(),
                new com.zhubao.erp.approval.domain.ApprovalDecisionCommand("APPROVE", "总经理", "资料已核对", ""));
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

    @Test
    void 服务拒绝非管理员的账号写操作() {
        Fixture fixture = fixture();
        OperatorContext manager = new OperatorContext("总经理", RoleCode.GENERAL_MANAGER, DataScope.ALL);
        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-Plan!", "计划员", "SALES", "ALL", true, false), manager))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.managementService().updateEnabled("operator", false, "停用", manager))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.managementService().unlock("operator", "解锁", manager))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.managementService().delete("operator", manager))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
    }

    @Test
    void 账号摘要与缺省保存保留独立接口的有效客户授权() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-Plan!", "计划员", "SALES", "CUSTOMER_RANGE",
                List.of("旧客户"), true, false), admin);
        fixture.customerAuthorizationService().revoke("planner", "旧客户");
        fixture.customerAuthorizationService().grant("planner", "新客户", "客户名称", "系统管理员");
        assertThat(fixture.managementService().accounts()).filteredOn(item -> item.username().equals("planner"))
                .singleElement().satisfies(item -> assertThat(item.customerScope()).containsExactly("新客户"));
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", null, "计划员", "SALES", "CUSTOMER_RANGE", true, false), admin);
        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner"))
                .singleElement().satisfies(item -> {
                    assertThat(item.customerCode()).isEqualTo("新客户");
                    assertThat(item.customerName()).isEqualTo("客户名称");
                });
    }

    @Test
    void 删除移除凭据客户授权会话并保留管理审计() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-Plan!", "计划员", "SALES", "CUSTOMER_RANGE",
                List.of("恒信珠宝"), true, true), admin);
        AuthSession target = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));
        AuthSession administrator = fixture.sessionService().login(new AuthLoginCommand("admin", "admin123"));
        OperatorContext context = fixture.sessionService().operatorContext("Bearer " + administrator.token()).orElseThrow();
        fixture.managementService().delete("planner", context);
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
        assertThat(fixture.customerAuthorizationService().authorizationsFor("planner")).isEmpty();
        assertThat(fixture.auditRepository().findRecent(20)).anySatisfy(event ->
                assertThat(event.reason()).contains("删除账号", "保留业务、审批及审计历史"));
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner", "Secure-2026-Other!", "新操作人", "SALES", "SELF", true, true), context);
        assertThatThrownBy(() -> fixture.sessionService().session("Bearer " + target.token()))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
    }

    @Test
    void 禁止删除当前登录账号() {
        Fixture fixture = fixture();
        AuthSession administrator = fixture.sessionService().login(new AuthLoginCommand("admin", "admin123"));
        OperatorContext context = fixture.sessionService().operatorContext("Bearer " + administrator.token()).orElseThrow();
        assertThatThrownBy(() -> fixture.managementService().delete("admin", context))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能删除当前登录账号");
        assertThat(fixture.accountRepository().findByUsername("admin")).isPresent();
    }

    @Test
    void 禁止停用降权或删除最后启用管理员() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.managementService().updateEnabled("admin", false, "停用", admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少一个启用的系统管理员");
        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "admin", null, "系统管理员", "SALES", "ALL", true, true), admin))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少一个启用的系统管理员");
        assertThatThrownBy(() -> fixture.managementService().delete("admin",
                new OperatorContext("其他管理员", RoleCode.ADMIN, DataScope.ALL)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少一个启用的系统管理员");
        assertThat(fixture.accountRepository().findByUsername("admin").orElseThrow().enabled()).isTrue();
    }

    @Test
    void 更新密码或权限立即吊销原有会话() {
        Fixture fixture = fixture();
        AuthSession target = fixture.sessionService().login(new AuthLoginCommand("operator", "operator123"));
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "operator", "Secure-2026-Other!", "操作工 王强", "RECEIVER", "DEPARTMENT", true, true), admin);
        assertThatThrownBy(() -> fixture.sessionService().operatorContext("Bearer " + target.token()))
                .isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
    }

    @Test
    void 保存或删除已提交后吊销失败仍返回成功且旧令牌被拒绝() {
        Fixture fixture = fixture();
        AuthSessionService failingSessions = new AuthSessionService(navigationMenuService, fixture.auditRepository(),
                fixture.accountRepository(), passwordHasher, passwordExpiryPolicy, passwordPolicy) {
            @Override
            public int revokeSessionsForUsername(String username, String reason) {
                throw new IllegalStateException("会话清理失败");
            }
        };
        AuthSession operator = failingSessions.login(new AuthLoginCommand("operator", "operator123"));
        AuthSession leader = failingSessions.login(new AuthLoginCommand("leader", "leader123"));
        AuthUserAccountManagementService service = new AuthUserAccountManagementService(fixture.accountRepository(),
                passwordHasher, passwordPolicy, passwordExpiryPolicy, fixture.auditRepository(), null, null, failingSessions);
        assertThat(service.upsert(new ManageAuthUserAccountCommand("operator", null, "新操作工", "SALES", "ALL", true, true), admin).role())
                .isEqualTo("SALES");
        service.delete("leader", new OperatorContext("其他管理员", RoleCode.ADMIN, DataScope.ALL));
        assertThat(fixture.accountRepository().findByUsername("leader")).isEmpty();
        assertThatThrownBy(() -> failingSessions.operatorContext("Bearer " + operator.token()))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("权限已变更");
        assertThatThrownBy(() -> failingSessions.session("Bearer " + leader.token()))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("已停用");
        assertThat(fixture.auditRepository().findRecent(20))
                .filteredOn(event -> event.eventType().equals("SESSION_REVOCATION") && event.result().equals("FAILURE"))
                .hasSize(2);
    }

    private Fixture fixture(boolean enforcePreApprovalForAccountActions) {
        InMemoryAuthAuditRepository auditRepository = new InMemoryAuthAuditRepository();
        InMemoryAuthUserAccountRepository accountRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        InMemoryApprovalTaskRepository approvalRepository = new InMemoryApprovalTaskRepository();
        InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        InMemoryDocumentManagementRepository documentRepository = new InMemoryDocumentManagementRepository();
        DocumentManagementService documentManagementService = new DocumentManagementService(
                documentRepository,
                localStorage()
        );
        ApprovalService approvalService = new ApprovalService(approvalRepository);
        InMemoryCustomerAuthorizationRepository customerAuthorizationRepository =
                new InMemoryCustomerAuthorizationRepository();
        CustomerAuthorizationService customerAuthorizationService =
                new CustomerAuthorizationService(customerAuthorizationRepository);
        AuthSessionService sessionService = new AuthSessionService(
                navigationMenuService,
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy,
                customerAuthorizationService
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
                documentRepository,
                documentManagementService,
                enforcePreApprovalForAccountActions,
                customerAuthorizationService
        );
        return new Fixture(
                accountRepository,
                auditRepository,
                approvalRepository,
                preApprovalApplicationRepository,
                documentRepository,
                managementService,
                sessionService,
                customerAuthorizationService
        );
    }

    private LocalAttachmentObjectStorage localStorage() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setLocalRoot(tempDir.toString());
        return new LocalAttachmentObjectStorage(properties);
    }

    private record Fixture(
            AuthUserAccountRepository accountRepository,
            InMemoryAuthAuditRepository auditRepository,
            InMemoryApprovalTaskRepository approvalRepository,
            InMemoryAuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            InMemoryDocumentManagementRepository documentRepository,
            AuthUserAccountManagementService managementService,
            AuthSessionService sessionService,
            CustomerAuthorizationService customerAuthorizationService
    ) {
    }
}
