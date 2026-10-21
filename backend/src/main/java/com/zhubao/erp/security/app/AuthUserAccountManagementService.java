package com.zhubao.erp.security.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.approval.domain.AccountPreApprovalTaskCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AccountAccessProfile;
import com.zhubao.erp.security.domain.AuthAccountOptions;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalEvidenceSummary;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalReadiness;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.CoverDuties;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.domain.SessionRevocationResult;
import com.zhubao.erp.security.infra.InMemoryAuthAccountActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuthUserAccountManagementService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter APPLICATION_CODE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong auditSequence = new AtomicLong();
    private final AtomicLong applicationSequence = new AtomicLong();
    private final AuthUserAccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final PasswordExpiryPolicy passwordExpiryPolicy;
    private final AuthAuditRepository authAuditRepository;
    private final ApprovalService approvalService;
    private final AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository;
    private final AuthSessionService authSessionService;
    private final DocumentManagementRepository documentManagementRepository;
    private final DocumentManagementService documentManagementService;
    private final boolean enforcePreApprovalForAccountActions;
    private final CustomerAuthorizationService customerAuthorizationService;
    private final AuthAccountActionRepository actionRepository;

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository
    ) {
        this(accountRepository, passwordHasher, passwordPolicy, passwordExpiryPolicy, authAuditRepository, null, null, null, null);
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService
    ) {
        this(accountRepository, passwordHasher, passwordPolicy, passwordExpiryPolicy, authAuditRepository, approvalService, null, null, null);
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                null,
                null
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            boolean enforcePreApprovalForAccountActions
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                null,
                null,
                enforcePreApprovalForAccountActions
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                authSessionService,
                null,
                false
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                authSessionService,
                documentManagementRepository,
                null,
                false
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository,
            boolean enforcePreApprovalForAccountActions
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                authSessionService,
                documentManagementRepository,
                null,
                enforcePreApprovalForAccountActions
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository,
            DocumentManagementService documentManagementService,
            boolean enforcePreApprovalForAccountActions
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                authSessionService,
                documentManagementRepository,
                documentManagementService,
                enforcePreApprovalForAccountActions,
                null,
                null
        );
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository,
            DocumentManagementService documentManagementService,
            boolean enforcePreApprovalForAccountActions,
            CustomerAuthorizationService customerAuthorizationService
    ) {
        this(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                authAuditRepository,
                approvalService,
                preApprovalApplicationRepository,
                authSessionService,
                documentManagementRepository,
                documentManagementService,
                enforcePreApprovalForAccountActions,
                customerAuthorizationService,
                null
        );
    }

    @Autowired
    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository,
            AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository,
            DocumentManagementService documentManagementService,
            @Value("${zhubao.auth.pre-approval.enforce-account-actions:false}")
            boolean enforcePreApprovalForAccountActions,
            CustomerAuthorizationService customerAuthorizationService,
            AuthAccountActionRepository actionRepository
    ) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordExpiryPolicy = passwordExpiryPolicy;
        this.authAuditRepository = authAuditRepository;
        this.approvalService = approvalService;
        this.preApprovalApplicationRepository = preApprovalApplicationRepository;
        this.authSessionService = authSessionService;
        this.documentManagementRepository = documentManagementRepository;
        this.documentManagementService = documentManagementService;
        this.enforcePreApprovalForAccountActions = enforcePreApprovalForAccountActions;
        this.customerAuthorizationService = customerAuthorizationService;
        this.actionRepository = actionRepository != null
                ? actionRepository
                : new InMemoryAuthAccountActionRepository(
                        accountRepository,
                        authAuditRepository,
                        preApprovalApplicationRepository,
                        customerAuthorizationService == null ? null : customerAuthorizationService.repository(),
                        null
                );
    }

    public List<AccountAccessProfile> accessInventory(PermissionGuard permissionGuard) {
        return accountRepository.findAll().stream()
                .map(account -> accessProfile(account, permissionGuard))
                .toList();
    }

    public List<AuthUserAccountSummary> accounts() {
        Instant now = Instant.now();
        return accountRepository.findAll().stream()
                .map(account -> summary(account, now))
                .toList();
    }

    public AuthAccountOptions accountOptions(PermissionGuard permissionGuard, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        return new AuthAccountOptions(java.util.Arrays.stream(RoleCode.values())
                .map(role -> new AuthAccountOptions.RoleOption(role.name(), roleLabel(role),
                        permissionGuard.allowedPermissionsFor(role).stream()
                                .sorted()
                                .map(permission -> new AuthAccountOptions.PermissionOption(permission.name(), permission.label()))
                                .toList()))
                .toList());
    }

    public List<AuthAccountPreApprovalReadiness> preApprovalReadiness() {
        return List.of(
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-CREATE-001",
                        "创建生产账号",
                        "系统管理员可直接新增账号；显式携带申请单时保留旧审批校验",
                        enforcePreApprovalForAccountActions ? "管理员直接管理；显式申请单校验审批通过态" : "管理员直接管理；显式申请单可校验",
                        List.of("账号申请单", "直属负责人审批", "系统管理员复核", "角色与数据范围说明", "初始密码交付确认"),
                        preApprovalMissingCapabilities("审批通过态强制校验", "正式电子签字", "审批后自动放行"),
                        List.of("GENERAL_MANAGER", "ADMIN"),
                        "当前可保存前置申请单、生成 ACCOUNT_PRE_APPROVAL 任务，并在强制模式校验任务 APPROVED 后放行账号动作",
                        "管理员可直接创建生产账号并保留审计",
                        "补正式电子签字、审批后自动放行和完整 IAM 账号生命周期",
                        "高",
                        "red"
                ),
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-ROLE-001",
                        "变更角色/数据范围",
                        "调整角色、dataScope 或 customerScope 会改变可见客户和敏感动作",
                        "管理员直接管理并保留审计",
                        List.of("变更原因", "原角色/新角色对比", "客户范围清单", "业务负责人确认", "回收计划"),
                        List.of("客户授权表", "审批前差异比对", "变更前冻结快照", "审批拒绝回滚"),
                        List.of("GENERAL_MANAGER", "ADMIN", "业务负责人"),
                        "账号审计会记录目标角色、数据范围和客户范围",
                        "管理员直接调整角色、数据范围和客户范围",
                        "补组织客户授权表和审批前差异校验",
                        "高",
                        "red"
                ),
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-PASSWORD-001",
                        "管理员重置密码",
                        "管理员重置生产账号密码前必须核实本人或离线交接场景",
                        "弱前置校验",
                        List.of("重置原因", "本人核验方式", "临时密码交付记录", "密码过期策略确认"),
                        List.of("本人核验附件"),
                        List.of("ADMIN", "账号负责人"),
                        "当前会刷新 passwordChangedAt、清零失败锁定、标记强制首次登录改密、消费首次临时密码登录通道并写 ACCOUNT_MANAGEMENT 审计",
                        "允许管理员重置，但必须保留 reason 和审计记录",
                        "补本人核验附件",
                        "中",
                        "amber"
                ),
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-STATUS-001",
                        "账号启停/解锁",
                        "停用、启用或解锁会直接影响现场登录能力",
                        "弱前置校验",
                        List.of("启停/解锁原因", "影响岗位", "交接状态", "管理员复核"),
                        List.of("岗位交接附件", "解锁本人确认"),
                        List.of("ADMIN", "GENERAL_MANAGER"),
                        "当前启停/解锁保留审计；停用账号会吊销现有 Bearer 会话并写 SESSION_REVOKED",
                        "试点允许管理员处理，生产需补岗位交接附件和解锁本人确认",
                        "补岗位交接附件和解锁本人确认",
                        "中",
                        "amber"
                )
        );
    }

    public AuthAccountPreApprovalDraft preApprovalDraft(
            AuthAccountPreApprovalDraftCommand command,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        PreApprovalAction action = parsePreApprovalAction(command.actionType());
        AuthAccountPreApprovalReadiness readiness = preApprovalReadiness().stream()
                .filter(item -> item.readinessCode().equals(action.readinessCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知账号前置审批动作：" + command.actionType()));
        String username = normalize(command.targetUsername());
        List<String> providedEvidence = normalizeList(command.providedEvidence());
        List<String> missingEvidence = readiness.requiredBeforeAction().stream()
                .filter(required -> providedEvidence.stream().noneMatch(provided -> evidenceMatches(required, provided)))
                .toList();
        // 完整 IAM 的扩展能力缺口继续展示，已实现的轻量审批只以本次材料完整性作为送审条件。
        boolean readyForBlockingApproval = missingEvidence.isEmpty();
        String blockingStatus = readyForBlockingApproval
                ? "可进入阻断式审批"
                : "缺少前置材料";
        String nextAction = missingEvidence.isEmpty()
                ? "提交审批人复核；完整 IAM 扩展要求见能力缺口说明"
                : "补齐前置材料：" + String.join(" / ", missingEvidence);
        return new AuthAccountPreApprovalDraft(
                "AUTH-PRE-DRAFT-" + action.name() + "-" + username.toUpperCase(Locale.ROOT),
                readiness.readinessCode(),
                action.actionType,
                username,
                operatorContext.operator(),
                operatorContext.role().name(),
                readiness.requiredBeforeAction(),
                providedEvidence,
                missingEvidence,
                readiness.missingCapabilities(),
                readiness.approverRoles(),
                blockingStatus,
                readyForBlockingApproval,
                buildPreApprovalDraftSummary(command, action, username),
                nextAction,
                readiness.riskLevel(),
                readiness.tone()
        );
    }

    public List<AuthAccountPreApprovalApplication> preApprovalApplications() {
        if (preApprovalApplicationRepository == null) {
            return List.of();
        }
        return preApprovalApplicationRepository.findAll().stream()
                .sorted(Comparator.comparing(AuthAccountPreApprovalApplication::submittedAt).reversed())
                .toList();
    }

    public List<AuthAccountPreApprovalEvidenceSummary> preApprovalEvidence() {
        List<EvidenceAttachment> accountPreApprovalAttachments = accountPreApprovalAttachments();
        return preApprovalApplications().stream()
                .map(application -> summarizePreApprovalEvidence(application, accountPreApprovalAttachments))
                .toList();
    }

    public AuthAccountPreApprovalEvidenceSummary uploadPreApprovalEvidence(
            String applicationCode,
            String fileName,
            String mediaType,
            byte[] content,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        if (documentManagementService == null || documentManagementRepository == null) {
            throw new BusinessException("账号前置审批证据上传未配置");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException("附件内容不能为空");
        }
        AuthAccountPreApprovalApplication application = requirePreApprovalApplication(applicationCode);
        String now = DISPLAY_TIME.format(Instant.now());
        documentManagementService.savePackage(preApprovalEvidencePackage(
                application,
                operatorContext.operator(),
                0,
                "待补" + requiredEvidenceForApplication(application),
                now,
                "amber"
        ));
        documentManagementService.uploadAttachment(
                preApprovalEvidenceDocumentCode(application),
                "账号前置审批",
                application.applicationCode(),
                fileName,
                mediaType,
                "账号前置审批上传",
                operatorContext.operator(),
                "保留账号前置审批附件并等待审批复核",
                content
        );
        List<EvidenceAttachment> accountPreApprovalAttachments = accountPreApprovalAttachments();
        int linkedEvidence = (int) accountPreApprovalAttachments.stream()
                .filter(attachment -> application.applicationCode().equals(attachment.businessCode()))
                .count();
        documentManagementService.savePackage(preApprovalEvidencePackage(
                application,
                operatorContext.operator(),
                linkedEvidence,
                "已关联证据",
                now,
                "green"
        ));
        return summarizePreApprovalEvidence(application, accountPreApprovalAttachments);
    }

    public AuthAccountPreApprovalApplication submitPreApprovalApplication(
            AuthAccountPreApprovalApplicationCommand command,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        AuthAccountPreApprovalDraft draft = preApprovalDraft(command.toDraftCommand(), operatorContext);
        String applicationStatus = applicationStatus(draft);
        AuthAccountPreApprovalApplication application = new AuthAccountPreApprovalApplication(
                nextPreApprovalApplicationCode(),
                draft.draftCode(),
                draft.readinessCode(),
                draft.actionType(),
                draft.targetUsername(),
                draft.applicant(),
                draft.applicantRole(),
                blankToDefault(command.targetOperator(), ""),
                blankToDefault(command.targetRole(), ""),
                blankToDefault(command.targetDataScope(), ""),
                normalizeList(command.targetCustomerScope()),
                command.targetEnabled(),
                command.demoAccount(),
                blankToDefault(command.reason(), ""),
                draft.requiredEvidence(),
                draft.providedEvidence(),
                draft.missingEvidence(),
                draft.missingCapabilities(),
                draft.approverRoles(),
                draft.blockingStatus(),
                applicationStatus,
                draft.readyForBlockingApproval(),
                draft.sourceSummary()
                        + "; applicationStatus=" + applicationStatus
                        + "; applicationBoundary=stored pre-approval application, not yet an enforcing IAM workflow",
                applicationNextAction(draft, applicationStatus),
                draft.riskLevel(),
                draft.tone(),
                "",
                DISPLAY_TIME.format(Instant.now()),
                WorkshopProcesses.bind(command.targetAllowedProcesses()),
                normalizeEnum(command.statusAction())
        );
        ApprovalTask task = createPreApprovalTask(application);
        return requirePreApprovalApplicationRepository().save(withPreApprovalTask(application, task));
    }

    public AuthUserAccountSummary upsert(ManageAuthUserAccountCommand command, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        String username = normalize(command.username());
        RoleCode role = parseRole(command.role());
        DataScope dataScope = parseDataScope(command.dataScope());
        AuthUserAccount existing = accountRepository.findByUsername(username).orElse(null);
        List<String> customerScope = customerScopeFor(dataScope, command.customerScope(), existing);
        boolean passwordProvided = command.password() != null && !command.password().isBlank();
        AuthAccountPreApprovalApplication preApprovalApplication = validatePreApprovalApplication(
                command.preApprovalApplicationCode(),
                preApprovalActionForUpsert(existing, passwordProvided, role, dataScope, customerScope, command.enabled()),
                username
        );
        List<String> allowedProcesses = command.allowedProcesses() == null
                ? (existing == null ? List.of() : existing.allowedProcesses())
                : WorkshopProcesses.bind(command.allowedProcesses());
        List<String> coverDuties = resolveCoverDuties(role, command.coverDuties(), existing);
        String teamCode = command.teamCode() == null ? (existing == null ? "" : existing.teamCode()) : command.teamCode();
        String departmentCode = command.departmentCode() == null
                ? (existing == null ? "" : existing.departmentCode()) : command.departmentCode();
        validateTargetSnapshot(preApprovalApplication, command, role, dataScope, customerScope, allowedProcesses);
        if (existing == null && !passwordProvided) {
            throw new BusinessException("新建账号必须提供初始密码");
        }
        if (passwordProvided) {
            passwordPolicy.assertAcceptable(command.password(), username);
        }
        String passwordHash = passwordProvided ? passwordHasher.hash(command.password()) : existing.passwordHash();
        Instant passwordChangedAt = passwordProvided ? Instant.now() : existing.passwordChangedAt();
        int failedLoginCount = existing == null || passwordProvided ? 0 : existing.failedLoginCount();
        Instant lockedUntil = existing == null || passwordProvided ? null : existing.lockedUntil();
        boolean passwordChangeRequired = passwordProvided ? !command.demoAccount() : existing.passwordChangeRequired();
        boolean temporaryPasswordConsumed = passwordProvided ? false : existing.temporaryPasswordConsumed();

        AuthUserAccount proposed = new AuthUserAccount(
                username,
                passwordHash,
                command.operator().trim(),
                role,
                dataScope,
                command.enabled(),
                command.demoAccount(),
                passwordChangedAt,
                failedLoginCount,
                lockedUntil,
                customerScope,
                passwordChangeRequired,
                temporaryPasswordConsumed,
                allowedProcesses,
                coverDuties,
                teamCode,
                departmentCode
        );
        String action = existing == null ? "创建账号" : passwordProvided ? "更新账号并重置密码" : "更新账号";
        String auditedAction = action
                + preApprovalApplicationSuffix(preApprovalApplication)
                + passwordChangeRequiredSuffix(passwordProvided, passwordChangeRequired);
        AuthAccountMutation mutation = AuthAccountMutation.save(
                proposed,
                newManagementAudit(username, operatorContext, auditedAction),
                preApprovalApplication
        );
        if (customerAuthorizationService != null) {
            mutation = mutation.withCustomerAuthorizations(customerAuthorizations(proposed, operatorContext));
        }
        actionRepository.commit(mutation);
        AuthUserAccount saved = requireAccount(username);
        if (existing != null && authSessionService != null) {
            revokeCredentialsAfterCommit(username, operatorContext, "账号已保存");
        }
        return summary(saved, Instant.now());
    }

    /** 客户授权与账号、审计一起提交；保留未变化授权的显示名称和授予记录。 */
    private List<CustomerAuthorization> customerAuthorizations(AuthUserAccount account, OperatorContext operatorContext) {
        if (account.dataScope() != DataScope.CUSTOMER_RANGE) return List.of();
        List<CustomerAuthorization> existing = customerAuthorizationService.authorizationsFor(account.username());
        return account.customerScope().stream().map(code -> existing.stream()
                .filter(item -> item.customerCode().equals(code)).findFirst()
                .orElseGet(() -> new CustomerAuthorization(account.username(), code, "", operatorContext.operator(),
                        java.time.LocalDateTime.now().toString())))
                .toList();
    }

    public AuthUserAccountSummary updateEnabled(String username, boolean enabled, String reason, OperatorContext operatorContext) {
        return updateEnabled(username, enabled, reason, null, operatorContext);
    }

    public AuthUserAccountSummary updateEnabled(
            String username,
            boolean enabled,
            String reason,
            String preApprovalApplicationCode,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        AuthAccountPreApprovalApplication preApprovalApplication = validatePreApprovalApplication(
                preApprovalApplicationCode,
                PreApprovalAction.CHANGE_STATUS,
                account.username()
        );
        validateStatusSnapshot(preApprovalApplication, enabled ? "ENABLE" : "DISABLE", enabled);
        String action = (enabled ? "启用账号" : "停用账号")
                + reasonSuffix(reason)
                + preApprovalApplicationSuffix(preApprovalApplication);
        String transactionalReason = action + (enabled || authSessionService == null ? "" : "；会话吊销在提交后执行");
        actionRepository.commit(AuthAccountMutation.setEnabled(
                account.username(),
                enabled,
                newManagementAudit(account.username(), operatorContext, transactionalReason),
                preApprovalApplication
        ));
        AuthUserAccount updated = requireAccount(account.username());
        revokeAfterCommit(updated, enabled, operatorContext);
        return summary(updated, Instant.now());
    }

    public AuthUserAccountSummary unlock(String username, String reason, OperatorContext operatorContext) {
        return unlock(username, reason, null, operatorContext);
    }

    public AuthUserAccountSummary unlock(
            String username,
            String reason,
            String preApprovalApplicationCode,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        AuthAccountPreApprovalApplication preApprovalApplication = validatePreApprovalApplication(
                preApprovalApplicationCode,
                PreApprovalAction.CHANGE_STATUS,
                account.username()
        );
        validateStatusSnapshot(preApprovalApplication, "UNLOCK", null);
        String action = "管理员解锁账号" + reasonSuffix(reason) + preApprovalApplicationSuffix(preApprovalApplication);
        actionRepository.commit(AuthAccountMutation.unlock(
                account.username(),
                newManagementAudit(account.username(), operatorContext, action),
                preApprovalApplication
        ));
        AuthUserAccount updated = requireAccount(account.username());
        return summary(updated, Instant.now());
    }

    public void delete(String username, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        String ownUsername = operatorContext.headerBased() || authSessionService == null ? ""
                : authSessionService.session("Bearer " + operatorContext.sessionToken())
                        .map(com.zhubao.erp.security.domain.AuthSession::username).orElse("");
        if (account.username().equals(normalize(ownUsername))
                || operatorContext.headerBased() && (account.operator().equals(operatorContext.operator())
                || account.username().equals(normalize(operatorContext.operator())))) {
            throw new BusinessException("不能删除当前登录账号");
        }
        actionRepository.commit(AuthAccountMutation.delete(account.username(),
                newManagementAudit(account.username(), operatorContext, "删除账号；保留业务、审批及审计历史")));
        if (authSessionService != null) {
            revokeCredentialsAfterCommit(account.username(), operatorContext, "账号已删除");
        }
    }

    /** 账号事务已提交，清理会话失败不把成功结果误报为回滚；旧令牌由解析时的账号状态和权限校验拒绝。 */
    private void revokeCredentialsAfterCommit(String username, OperatorContext operatorContext, String action) {
        try {
            authSessionService.revokeSessionsForUsername(username, action + "；管理员：" + operatorContext.operator());
        } catch (RuntimeException exception) {
            try {
                recordFollowUpAudit(username, operatorContext, "FAILURE",
                        action + "；会话清理失败，旧令牌由账号状态、密码与权限校验拒绝");
            } catch (RuntimeException ignored) {
                // 会话清理审计失败不改变已经提交的账号结果
            }
        }
    }

    private void requireAdmin(OperatorContext operatorContext) {
        if (operatorContext == null || operatorContext.role() != RoleCode.ADMIN) {
            throw new PermissionDeniedException("仅系统管理员可以管理登录账号");
        }
    }

    /**
     * 账号已经停用且申请已消费后，只重试会话吊销。不会再次修改账号，也不会再次消费申请。
     * 会话存放在进程内存，不能加入账号数据库事务；吊销失败时停用结果保持有效。
     */
    public SessionRevocationResult retrySessionRevocation(String username, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        if (account.enabled()) {
            throw new BusinessException("账号仍处于启用状态，不能单独重试会话吊销：" + account.username());
        }
        if (authSessionService == null) {
            throw new BusinessException("会话服务未配置，无法重试会话吊销：" + account.username());
        }
        try {
            int revoked = authSessionService.revokeSessionsForUsername(
                    account.username(),
                    "Retry session revocation by " + operatorContext.operator() + "; account=" + account.username()
            );
            recordFollowUpAudit(account.username(), operatorContext, "SUCCESS",
                    "重试会话吊销完成；revokedSessions=" + revoked);
            return new SessionRevocationResult(account.username(), revoked);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException("会话吊销仍然失败。请稍后再只重试会话吊销，不要再次执行该申请：" + account.username());
        }
    }

    private AuthUserAccount requireAccount(String username) {
        return accountRepository.findByUsername(normalize(username))
                .orElseThrow(() -> new BusinessException("账号不存在：" + username));
    }

    private AccountAccessProfile accessProfile(AuthUserAccount account, PermissionGuard permissionGuard) {
        String source = switch (account.role()) {
            case ADMIN -> "管理员";
            case BUSINESS_MANAGER -> "顶岗";
            default -> "角色模板";
        };
        List<AccountAccessProfile.PermissionGrant> grants = permissionGuard
                .effectivePermissions(account.role(), account.coverDuties()).stream()
                .map(permission -> new AccountAccessProfile.PermissionGrant(permission.name(), permission.label(), source))
                .toList();
        return new AccountAccessProfile(
                account.username(),
                account.operator(),
                account.role().name(),
                account.dataScope().name(),
                effectiveCustomerScope(account),
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode(),
                grants
        );
    }

    private AuthUserAccountSummary summary(AuthUserAccount account, Instant now) {
        boolean locked = account.lockedUntil() != null && account.lockedUntil().isAfter(now);
        return new AuthUserAccountSummary(
                account.username(),
                account.operator(),
                account.role().name(),
                roleLabel(account.role()),
                account.dataScope().name(),
                dataScopeLabel(account.dataScope()),
                effectiveCustomerScope(account),
                account.enabled(),
                account.demoAccount(),
                displayInstant(account.passwordChangedAt()),
                DISPLAY_TIME.format(passwordExpiryPolicy.expiresAt(account)),
                passwordExpiryPolicy.isExpired(account, now),
                account.passwordChangeRequired(),
                account.failedLoginCount(),
                account.lockedUntil() == null ? "" : DISPLAY_TIME.format(account.lockedUntil()),
                locked,
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode()
        );
    }

    private List<String> resolveCoverDuties(RoleCode role, List<String> requested, AuthUserAccount existing) {
        if (role != RoleCode.BUSINESS_MANAGER) {
            return List.of();
        }
        List<String> duties = requested == null
                ? (existing == null || existing.coverDuties().isEmpty() ? CoverDuties.packageDefaults() : existing.coverDuties())
                : CoverDuties.normalize(requested);
        if (duties.isEmpty()) {
            throw new BusinessException("业务管理至少保留一项权限");
        }
        return duties;
    }

    private List<String> effectiveCustomerScope(AuthUserAccount account) {
        return account.dataScope() == DataScope.CUSTOMER_RANGE && customerAuthorizationService != null
                ? customerAuthorizationService.effectiveCustomerScope(account.username(), account.customerScope())
                : account.customerScope();
    }

    private AuthAuditEvent newManagementAudit(String targetUsername, OperatorContext operatorContext, String reason) {
        return newAudit("AUTH-ACC-", "ACCOUNT_MANAGEMENT", targetUsername, operatorContext, "SUCCESS", reason);
    }

    private void recordFollowUpAudit(String targetUsername, OperatorContext operatorContext, String result, String reason) {
        authAuditRepository.save(newAudit("AUTH-REV-", "SESSION_REVOCATION", targetUsername, operatorContext, result, reason));
    }

    private AuthAuditEvent newAudit(
            String prefix,
            String eventType,
            String targetUsername,
            OperatorContext operatorContext,
            String result,
            String reason
    ) {
        return new AuthAuditEvent(
                prefix + DISPLAY_TIME.format(Instant.now()).replace(" ", "-").replace(":", "") + "-"
                        + String.format("%04d", auditSequence.incrementAndGet()),
                eventType,
                targetUsername,
                operatorContext.operator(),
                operatorContext.role().name(),
                result,
                reason,
                DISPLAY_TIME.format(Instant.now()),
                tokenSuffix(operatorContext.sessionToken())
        );
    }

    /**
     * 会话吊销发生在账号事务提交之后。失败时不回滚停用，避免申请重新变为可执行；调用方只重试吊销。
     * 即使吊销失败，后续解析会话也会因账号已停用而拒绝，避免旧令牌继续访问。
     */
    private int revokeAfterCommit(AuthUserAccount account, boolean enabled, OperatorContext operatorContext) {
        if (enabled || authSessionService == null) {
            return 0;
        }
        try {
            int revoked = authSessionService.revokeSessionsForUsername(
                    account.username(),
                    "Account disabled by " + operatorContext.operator() + "; account=" + account.username()
            );
            recordFollowUpAudit(account.username(), operatorContext, "SUCCESS",
                    "停用账号后的会话吊销完成；revokedSessions=" + revoked);
            return revoked;
        } catch (RuntimeException ex) {
            try {
                recordFollowUpAudit(account.username(), operatorContext, "FAILURE",
                        "账号已停用，会话吊销失败，请只重试会话吊销");
            } catch (RuntimeException ignored) {
                // 吊销结果审计失败时，已提交的停用和申请消费仍然保留
            }
            throw new BusinessException("账号已停用，会话吊销失败。请只重试会话吊销，不要再次执行该申请：" + account.username());
        }
    }

    private RoleCode parseRole(String rawRole) {
        try {
            return RoleCode.valueOf(normalizeEnum(rawRole));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知角色：" + rawRole);
        }
    }

    private DataScope parseDataScope(String rawScope) {
        try {
            return DataScope.valueOf(normalizeEnum(rawScope));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知数据范围：" + rawScope);
        }
    }

    private List<String> customerScopeFor(
            DataScope dataScope,
            List<String> requestedScope,
            AuthUserAccount existing
    ) {
        if (dataScope != DataScope.CUSTOMER_RANGE) {
            return List.of();
        }
        if (requestedScope == null) {
            return existing == null ? List.of() : effectiveCustomerScope(existing);
        }
        return requestedScope.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean evidenceMatches(String required, String provided) {
        String normalizedRequired = required.trim();
        String normalizedProvided = provided.trim();
        return normalizedProvided.equals(normalizedRequired)
                || normalizedProvided.contains(normalizedRequired);
    }

    private String applicationStatus(AuthAccountPreApprovalDraft draft) {
        if (!draft.missingEvidence().isEmpty()) {
            return "WAITING_EVIDENCE";
        }
        return "READY_FOR_BLOCKING_APPROVAL";
    }

    private String applicationNextAction(AuthAccountPreApprovalDraft draft, String applicationStatus) {
        return switch (applicationStatus) {
            case "WAITING_EVIDENCE" -> draft.nextAction();
            case "WAITING_SYSTEM_CAPABILITY" -> "补齐系统能力：" + String.join(" / ", draft.missingCapabilities());
            default -> "提交审批人复核并冻结账号动作放行条件";
        };
    }

    private List<EvidenceAttachment> accountPreApprovalAttachments() {
        if (documentManagementRepository == null) {
            return List.of();
        }
        return documentManagementRepository.attachments().stream()
                .filter(attachment -> "账号前置审批".equals(attachment.businessType()))
                .toList();
    }

    private AuthAccountPreApprovalApplication requirePreApprovalApplication(String applicationCode) {
        String normalizedCode = blankToDefault(applicationCode, "");
        if (normalizedCode.isBlank()) {
            throw new BusinessException("账号前置审批申请单号不能为空");
        }
        return requirePreApprovalApplicationRepository()
                .findByCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("账号前置审批申请单不存在：" + normalizedCode));
    }

    private String preApprovalEvidenceDocumentCode(AuthAccountPreApprovalApplication application) {
        return "DOC-" + application.applicationCode();
    }

    private DocumentPackage preApprovalEvidencePackage(
            AuthAccountPreApprovalApplication application,
            String owner,
            int linkedEvidence,
            String status,
            String updatedAt,
            String tone
    ) {
        return new DocumentPackage(
                preApprovalEvidenceDocumentCode(application),
                application.actionType() + "证据包",
                "账号前置审批",
                application.applicationCode(),
                owner,
                "归档账号前置审批申请单、本人核验、岗位交接和审批复核证据。",
                1,
                linkedEvidence,
                status,
                "账号审批证据长期保留",
                updatedAt,
                tone
        );
    }

    private AuthAccountPreApprovalEvidenceSummary summarizePreApprovalEvidence(
            AuthAccountPreApprovalApplication application,
            List<EvidenceAttachment> accountPreApprovalAttachments
    ) {
        String requiredEvidence = requiredEvidenceForApplication(application);
        List<EvidenceAttachment> matchedAttachments = accountPreApprovalAttachments.stream()
                .filter(attachment -> application.applicationCode().equals(attachment.businessCode()))
                .toList();
        if (matchedAttachments.isEmpty()) {
            return new AuthAccountPreApprovalEvidenceSummary(
                    application.applicationCode(),
                    application.readinessCode(),
                    application.actionType(),
                    application.targetUsername(),
                    requiredEvidence,
                    "",
                    0,
                    1,
                    List.of(),
                    "",
                    "待补" + requiredEvidence,
                    "上传或绑定 " + requiredEvidence + "，businessType=账号前置审批，businessCode="
                            + application.applicationCode(),
                    "red"
            );
        }

        int missingEvidence = (int) matchedAttachments.stream()
                .filter(this::missingEvidenceAttachment)
                .count();
        EvidenceAttachment latest = matchedAttachments.stream()
                .max(Comparator.comparing(EvidenceAttachment::uploadedAt))
                .orElse(matchedAttachments.get(0));
        String nextAction = matchedAttachments.stream()
                .map(EvidenceAttachment::fallbackAction)
                .filter(action -> action != null && !action.isBlank() && !"无".equals(action))
                .findFirst()
                .orElse("保留账号前置审批附件并等待审批复核");

        return new AuthAccountPreApprovalEvidenceSummary(
                application.applicationCode(),
                application.readinessCode(),
                application.actionType(),
                application.targetUsername(),
                requiredEvidence,
                latest.documentCode(),
                matchedAttachments.size(),
                missingEvidence,
                matchedAttachments.stream().map(EvidenceAttachment::code).toList(),
                latest.uploadedAt(),
                missingEvidence > 0 ? "证据待补" : "已关联证据",
                nextAction,
                missingEvidence > 0 ? "amber" : latest.tone()
        );
    }

    private String requiredEvidenceForApplication(AuthAccountPreApprovalApplication application) {
        if ("AUTH-PRE-PASSWORD-001".equals(application.readinessCode())) {
            return "本人核验附件";
        }
        if ("AUTH-PRE-STATUS-001".equals(application.readinessCode())) {
            return "岗位交接或本人确认附件";
        }
        return "账号前置审批附件";
    }

    private boolean missingEvidenceAttachment(EvidenceAttachment attachment) {
        return attachment.status().contains("缺失")
                || attachment.status().contains("待上传")
                || attachment.storageLocation().contains("待上传")
                || attachment.storageLocation().contains("待归档")
                || attachment.checksum().contains("待生成");
    }

    private ApprovalTask createPreApprovalTask(AuthAccountPreApprovalApplication application) {
        if (approvalService == null) {
            return null;
        }
        return approvalService.createAccountPreApprovalTask(new AccountPreApprovalTaskCommand(
                application.applicationCode(),
                application.readinessCode(),
                application.actionType(),
                application.targetUsername(),
                application.applicant(),
                application.applicantRole(),
                application.targetOperator(),
                application.targetRole(),
                application.targetDataScope(),
                application.targetCustomerScope(),
                application.applicationStatus(),
                application.blockingStatus(),
                application.readyForBlockingApproval(),
                application.missingEvidence(),
                application.missingCapabilities(),
                application.approverRoles(),
                application.riskLevel(),
                application.sourceSummary()
        ));
    }

    private AuthAccountPreApprovalApplication withPreApprovalTask(
            AuthAccountPreApprovalApplication application,
            ApprovalTask task
    ) {
        if (task == null) {
            return application;
        }
        return new AuthAccountPreApprovalApplication(
                application.applicationCode(),
                application.draftCode(),
                application.readinessCode(),
                application.actionType(),
                application.targetUsername(),
                application.applicant(),
                application.applicantRole(),
                application.targetOperator(),
                application.targetRole(),
                application.targetDataScope(),
                application.targetCustomerScope(),
                application.targetEnabled(),
                application.demoAccount(),
                application.reason(),
                application.requiredEvidence(),
                application.providedEvidence(),
                application.missingEvidence(),
                application.missingCapabilities(),
                application.approverRoles(),
                application.blockingStatus(),
                application.applicationStatus(),
                application.readyForBlockingApproval(),
                application.sourceSummary() + "; approvalTaskCode=" + task.approvalCode(),
                "Generated lightweight approval task: " + task.approvalCode() + "; " + application.nextAction(),
                application.riskLevel(),
                application.tone(),
                task.approvalCode(),
                application.submittedAt(),
                application.targetAllowedProcesses(),
                application.statusAction()
        );
    }

    private AuthAccountPreApprovalApplication validatePreApprovalApplication(
            String applicationCode,
            PreApprovalAction expectedAction,
            String targetUsername
    ) {
        String normalizedCode = blankToDefault(applicationCode, "");
        if (normalizedCode.isBlank()) {
            return null;
        }
        AuthAccountPreApprovalApplication application = requirePreApprovalApplicationRepository()
                .findByCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("账号前置审批申请单不存在：" + normalizedCode));
        if (!normalize(application.targetUsername()).equals(normalize(targetUsername))) {
            throw new BusinessException("账号前置审批申请单目标账号不匹配：" + normalizedCode);
        }
        if (!expectedAction.readinessCode.equals(application.readinessCode())) {
            throw new BusinessException("账号前置审批申请单动作类型不匹配：" + normalizedCode);
        }
        if (!"READY_FOR_BLOCKING_APPROVAL".equals(application.applicationStatus())
                || !application.readyForBlockingApproval()) {
            throw new BusinessException("账号前置审批申请单尚未具备账号动作放行条件：" + normalizedCode);
        }
        if (enforcePreApprovalForAccountActions) {
            requireApprovedPreApprovalTask(application);
        }
        return application;
    }

    private void validateTargetSnapshot(AuthAccountPreApprovalApplication application, ManageAuthUserAccountCommand command,
                                        RoleCode role, DataScope scope, List<String> customerScope, List<String> processes) {
        if (application == null) return;
        boolean matches = blankToDefault(application.targetOperator(), "").equals(blankToDefault(command.operator(), ""))
                && normalizeEnum(application.targetRole()).equals(role.name())
                && normalizeEnum(application.targetDataScope()).equals(scope.name())
                && new java.util.HashSet<>(normalizeList(application.targetCustomerScope())).equals(new java.util.HashSet<>(customerScope))
                && java.util.Objects.equals(application.targetEnabled(), command.enabled())
                && java.util.Objects.equals(application.demoAccount(), command.demoAccount())
                && new java.util.HashSet<>(WorkshopProcesses.bind(application.targetAllowedProcesses())).equals(new java.util.HashSet<>(processes));
        if (!matches) throw new BusinessException("账号动作与已批准申请的目标资料、权限或工序不一致：" + application.applicationCode());
        if ("AUTH-PRE-STATUS-001".equals(application.readinessCode())) {
            validateStatusSnapshot(application, command.enabled() ? "ENABLE" : "DISABLE", command.enabled());
        }
    }

    private void validateStatusSnapshot(AuthAccountPreApprovalApplication application, String action, Boolean enabled) {
        if (application == null) return;
        if (!action.equals(normalizeEnum(application.statusAction()))
                || enabled != null && !java.util.Objects.equals(enabled, application.targetEnabled())) {
            throw new BusinessException("账号启停或解锁动作与已批准申请不一致：" + application.applicationCode());
        }
    }

    private void requireApprovedPreApprovalTask(AuthAccountPreApprovalApplication application) {
        String approvalTaskCode = blankToDefault(application.approvalTaskCode(), "");
        if (approvalTaskCode.isBlank()) {
            throw new BusinessException("Account pre-approval application has no approval task: "
                    + application.applicationCode());
        }
        if (approvalService == null) {
            throw new BusinessException("Approval service is not configured for account pre-approval enforcement");
        }
        ApprovalTask task = approvalService.findByCode(approvalTaskCode);
        if (!"ACCOUNT_PRE_APPROVAL".equals(task.scene())) {
            throw new BusinessException("Approval task is not an account pre-approval task: " + approvalTaskCode);
        }
        if (!blankToDefault(task.businessCode(), "").equals(blankToDefault(application.applicationCode(), ""))) {
            throw new BusinessException("Approval task business code does not match application: "
                    + approvalTaskCode + " / " + application.applicationCode());
        }
        if (task.status() != ApprovalStatus.APPROVED) {
            throw new BusinessException("Account pre-approval task is not approved: "
                    + approvalTaskCode + " status=" + task.status());
        }
    }

    private PreApprovalAction preApprovalActionForUpsert(
            AuthUserAccount existing,
            boolean passwordProvided,
            RoleCode requestedRole,
            DataScope requestedDataScope,
            List<String> requestedCustomerScope,
            boolean requestedEnabled
    ) {
        if (existing == null) {
            return PreApprovalAction.CREATE_ACCOUNT;
        }
        if (passwordProvided) {
            return PreApprovalAction.RESET_PASSWORD;
        }
        if (existing.enabled() != requestedEnabled) {
            return PreApprovalAction.CHANGE_STATUS;
        }
        if (existing.role() != requestedRole
                || existing.dataScope() != requestedDataScope
                || !existing.customerScope().equals(requestedCustomerScope)) {
            return PreApprovalAction.CHANGE_ROLE_SCOPE;
        }
        return PreApprovalAction.CHANGE_ROLE_SCOPE;
    }

    private String preApprovalApplicationSuffix(AuthAccountPreApprovalApplication application) {
        return application == null ? "" : "；前置申请单：" + application.applicationCode();
    }

    private String passwordChangeRequiredSuffix(boolean passwordProvided, boolean passwordChangeRequired) {
        return passwordProvided ? "; passwordChangeRequired=" + passwordChangeRequired : "";
    }

    private AuthAccountPreApprovalApplicationRepository requirePreApprovalApplicationRepository() {
        if (preApprovalApplicationRepository == null) {
            throw new BusinessException("账号前置审批申请单仓储未配置");
        }
        return preApprovalApplicationRepository;
    }

    private List<String> preApprovalMissingCapabilities(String... capabilities) {
        if (enforcePreApprovalForAccountActions) {
            return List.of(capabilities).stream()
                    .filter(capability -> !"审批通过态强制校验".equals(capability))
                    .toList();
        }
        ArrayList<String> result = new ArrayList<>();
        result.add("全局强制申请单号");
        result.addAll(List.of(capabilities));
        return List.copyOf(result);
    }

    private PreApprovalAction parsePreApprovalAction(String rawAction) {
        String normalized = normalizeEnum(rawAction);
        for (PreApprovalAction action : PreApprovalAction.values()) {
            if (normalized.equals(action.name())
                    || normalized.equals(normalizeEnum(action.readinessCode))
                    || normalized.equals(normalizeEnum(action.actionType))) {
                return action;
            }
        }
        throw new BusinessException("未知账号前置审批动作：" + rawAction);
    }

    private String buildPreApprovalDraftSummary(
            AuthAccountPreApprovalDraftCommand command,
            PreApprovalAction action,
            String username
    ) {
        return "action=" + action.actionType
                + "; targetUsername=" + username
                + "; targetOperator=" + blankToDefault(command.targetOperator(), "")
                + "; targetRole=" + blankToDefault(command.targetRole(), "")
                + "; targetDataScope=" + blankToDefault(command.targetDataScope(), "")
                + "; targetCustomerScope=" + String.join("|", normalizeList(command.targetCustomerScope()))
                + "; targetAllowedProcesses=" + String.join("|", WorkshopProcesses.bind(command.targetAllowedProcesses()))
                + "; statusAction=" + normalizeEnum(command.statusAction())
                + "; targetEnabled=" + (command.targetEnabled() == null ? "" : command.targetEnabled())
                + "; demoAccount=" + (command.demoAccount() == null ? "" : command.demoAccount())
                + "; reason=" + blankToDefault(command.reason(), "")
                + "; boundary=pre-action draft only, not a blocking IAM workflow";
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nextPreApprovalApplicationCode() {
        return "AUTH-PRE-APP-" + APPLICATION_CODE_TIME.format(Instant.now())
                + "-" + String.format("%04d", applicationSequence.incrementAndGet());
    }

    private String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : "；原因：" + reason.trim();
    }

    private String displayInstant(Instant value) {
        return value == null ? "" : DISPLAY_TIME.format(value);
    }

    private String tokenSuffix(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        int start = Math.max(0, token.length() - 6);
        return token.substring(start);
    }

    private String roleLabel(RoleCode role) {
        return switch (role) {
            case ADMIN -> "系统管理员";
            case GENERAL_MANAGER -> "总经理";
            case BUSINESS_MANAGER -> "业务管理";
            case SALES -> "业务员";
            case RECEIVER -> "收货员";
            case WORKSHOP_LEADER -> "车间组长";
            case OPERATOR -> "操作工";
            case QUALITY_INSPECTOR -> "质检员";
            case WAREHOUSE_KEEPER -> "库管员";
            case FINANCE_STATISTICIAN -> "财务统计";
        };
    }

    private String dataScopeLabel(DataScope dataScope) {
        return switch (dataScope) {
            case SELF -> "本人任务";
            case TEAM -> "本班组/工位";
            case DEPARTMENT -> "本部门";
            case CUSTOMER_RANGE -> "授权客户范围";
            case ALL -> "全部数据";
        };
    }

    private enum PreApprovalAction {
        CREATE_ACCOUNT("AUTH-PRE-CREATE-001", "创建生产账号"),
        CHANGE_ROLE_SCOPE("AUTH-PRE-ROLE-001", "变更角色/数据范围"),
        RESET_PASSWORD("AUTH-PRE-PASSWORD-001", "管理员重置密码"),
        CHANGE_STATUS("AUTH-PRE-STATUS-001", "账号启停/解锁");

        private final String readinessCode;
        private final String actionType;

        PreApprovalAction(String readinessCode, String actionType) {
            this.readinessCode = readinessCode;
            this.actionType = actionType;
        }
    }
}
