package com.zhubao.erp.security.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.AccountPreApprovalTaskCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalReadiness;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
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
    private final boolean enforcePreApprovalForAccountActions;

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository
    ) {
        this(accountRepository, passwordHasher, passwordPolicy, passwordExpiryPolicy, authAuditRepository, null, null);
    }

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordExpiryPolicy passwordExpiryPolicy,
            AuthAuditRepository authAuditRepository,
            ApprovalService approvalService
    ) {
        this(accountRepository, passwordHasher, passwordPolicy, passwordExpiryPolicy, authAuditRepository, approvalService, null);
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
                false
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
            @Value("${zhubao.auth.pre-approval.enforce-account-actions:false}")
            boolean enforcePreApprovalForAccountActions
    ) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordExpiryPolicy = passwordExpiryPolicy;
        this.authAuditRepository = authAuditRepository;
        this.approvalService = approvalService;
        this.preApprovalApplicationRepository = preApprovalApplicationRepository;
        this.enforcePreApprovalForAccountActions = enforcePreApprovalForAccountActions;
    }

    public List<AuthUserAccountSummary> accounts() {
        Instant now = Instant.now();
        return accountRepository.findAll().stream()
                .map(account -> summary(account, now))
                .toList();
    }

    public List<AuthAccountPreApprovalReadiness> preApprovalReadiness() {
        return List.of(
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-CREATE-001",
                        "创建生产账号",
                        "新增非 demo 账号或授予敏感角色前必须先完成申请与审批",
                        enforcePreApprovalForAccountActions ? "申请单号和审批通过态强制校验已开启" : "申请单号可校验，默认不强制",
                        List.of("账号申请单", "直属负责人审批", "系统管理员复核", "角色与数据范围说明", "初始密码交付确认"),
                        preApprovalMissingCapabilities("审批通过态强制校验", "正式电子签字", "审批后自动放行"),
                        List.of("GENERAL_MANAGER", "ADMIN"),
                        "当前可保存前置申请单、生成 ACCOUNT_PRE_APPROVAL 任务，并在强制模式校验任务 APPROVED 后放行账号动作",
                        "生产上线前禁止直接创建生产账号",
                        "补正式电子签字、审批后自动放行和完整 IAM 账号生命周期",
                        "高",
                        "red"
                ),
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-ROLE-001",
                        "变更角色/数据范围",
                        "调整角色、dataScope 或 customerScope 会改变可见客户和敏感动作",
                        "仅事后审批摘要",
                        List.of("变更原因", "原角色/新角色对比", "客户范围清单", "业务负责人确认", "回收计划"),
                        List.of("客户授权表", "审批前差异比对", "变更前冻结快照", "审批拒绝回滚"),
                        List.of("GENERAL_MANAGER", "ADMIN", "业务负责人"),
                        "账号审计会记录目标角色、数据范围和客户范围",
                        "生产上线前仅允许评审环境通过账号管理直接调整",
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
                        List.of("本人核验附件", "临时密码一次性通道", "强制首次登录改密"),
                        List.of("ADMIN", "账号负责人"),
                        "当前会刷新 passwordChangedAt、清零失败锁定并写 ACCOUNT_MANAGEMENT 审计",
                        "允许管理员重置，但必须保留 reason 和审计记录",
                        "补本人核验附件和首次登录强制改密标记",
                        "中",
                        "amber"
                ),
                new AuthAccountPreApprovalReadiness(
                        "AUTH-PRE-STATUS-001",
                        "账号启停/解锁",
                        "停用、启用或解锁会直接影响现场登录能力",
                        "弱前置校验",
                        List.of("启停/解锁原因", "影响岗位", "交接状态", "管理员复核"),
                        List.of("岗位交接附件", "解锁本人确认", "停用后会话吊销"),
                        List.of("ADMIN", "GENERAL_MANAGER"),
                        "当前启停/解锁会写审计并生成事后复核任务",
                        "试点允许管理员处理，生产需补停用会话吊销",
                        "补会话吊销、岗位交接附件和解锁本人确认",
                        "中",
                        "amber"
                )
        );
    }

    public AuthAccountPreApprovalDraft preApprovalDraft(
            AuthAccountPreApprovalDraftCommand command,
            OperatorContext operatorContext
    ) {
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
        boolean readyForBlockingApproval = missingEvidence.isEmpty() && readiness.missingCapabilities().isEmpty();
        String blockingStatus = readyForBlockingApproval
                ? "可进入阻断式审批"
                : missingEvidence.isEmpty() ? "系统缺阻断式审批能力" : "缺少前置材料";
        String nextAction = missingEvidence.isEmpty()
                ? readiness.nextAction()
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

    public AuthAccountPreApprovalApplication submitPreApprovalApplication(
            AuthAccountPreApprovalApplicationCommand command,
            OperatorContext operatorContext
    ) {
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
                DISPLAY_TIME.format(Instant.now())
        );
        ApprovalTask task = createPreApprovalTask(application);
        return requirePreApprovalApplicationRepository().save(withPreApprovalTask(application, task));
    }

    public AuthUserAccountSummary upsert(ManageAuthUserAccountCommand command, OperatorContext operatorContext) {
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
        AuthUserAccount saved = accountRepository.save(new AuthUserAccount(
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
                customerScope
        ));
        String action = existing == null ? "创建账号" : passwordProvided ? "更新账号并重置密码" : "更新账号";
        String auditedAction = action + preApprovalApplicationSuffix(preApprovalApplication);
        recordManagementAudit(username, operatorContext, auditedAction);
        recordApprovalSummary(saved, operatorContext, auditedAction);
        return summary(saved, Instant.now());
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
        AuthUserAccount account = requireAccount(username);
        AuthAccountPreApprovalApplication preApprovalApplication = validatePreApprovalApplication(
                preApprovalApplicationCode,
                PreApprovalAction.CHANGE_STATUS,
                account.username()
        );
        accountRepository.updateEnabled(account.username(), enabled);
        AuthUserAccount updated = requireAccount(account.username());
        String action = (enabled ? "启用账号" : "停用账号")
                + reasonSuffix(reason)
                + preApprovalApplicationSuffix(preApprovalApplication);
        recordManagementAudit(account.username(), operatorContext, action);
        recordApprovalSummary(updated, operatorContext, action);
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
        AuthUserAccount account = requireAccount(username);
        AuthAccountPreApprovalApplication preApprovalApplication = validatePreApprovalApplication(
                preApprovalApplicationCode,
                PreApprovalAction.CHANGE_STATUS,
                account.username()
        );
        accountRepository.unlock(account.username());
        AuthUserAccount updated = requireAccount(account.username());
        String action = "管理员解锁账号" + reasonSuffix(reason) + preApprovalApplicationSuffix(preApprovalApplication);
        recordManagementAudit(account.username(), operatorContext, action);
        recordApprovalSummary(updated, operatorContext, action);
        return summary(updated, Instant.now());
    }

    private AuthUserAccount requireAccount(String username) {
        return accountRepository.findByUsername(normalize(username))
                .orElseThrow(() -> new BusinessException("账号不存在：" + username));
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
                account.customerScope(),
                account.enabled(),
                account.demoAccount(),
                displayInstant(account.passwordChangedAt()),
                DISPLAY_TIME.format(passwordExpiryPolicy.expiresAt(account)),
                passwordExpiryPolicy.isExpired(account, now),
                account.failedLoginCount(),
                account.lockedUntil() == null ? "" : DISPLAY_TIME.format(account.lockedUntil()),
                locked
        );
    }

    private void recordManagementAudit(String targetUsername, OperatorContext operatorContext, String reason) {
        authAuditRepository.save(new AuthAuditEvent(
                "AUTH-ACC-" + DISPLAY_TIME.format(Instant.now()).replace(" ", "-").replace(":", "") + "-"
                        + String.format("%04d", auditSequence.incrementAndGet()),
                "ACCOUNT_MANAGEMENT",
                targetUsername,
                operatorContext.operator(),
                operatorContext.role().name(),
                "SUCCESS",
                reason,
                DISPLAY_TIME.format(Instant.now()),
                tokenSuffix(operatorContext.sessionToken())
        ));
    }

    private void recordApprovalSummary(AuthUserAccount account, OperatorContext operatorContext, String action) {
        if (approvalService == null) {
            return;
        }
        approvalService.createAccountManagementApprovalTask(
                account.username(),
                action,
                operatorContext.operator(),
                operatorContext.role().name(),
                account.operator(),
                account.role().name(),
                account.dataScope().name(),
                String.join("|", account.customerScope()),
                account.enabled(),
                account.demoAccount()
        );
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
            return existing == null ? List.of() : existing.customerScope();
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
        if (!draft.missingCapabilities().isEmpty()) {
            return "WAITING_SYSTEM_CAPABILITY";
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
                application.submittedAt()
        );
    }

    private AuthAccountPreApprovalApplication validatePreApprovalApplication(
            String applicationCode,
            PreApprovalAction expectedAction,
            String targetUsername
    ) {
        String normalizedCode = blankToDefault(applicationCode, "");
        if (normalizedCode.isBlank()) {
            if (enforcePreApprovalForAccountActions) {
                throw new BusinessException("账号动作已启用前置审批强制开关，必须提供申请单号："
                        + expectedAction.readinessCode + " / " + normalize(targetUsername));
            }
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
