package com.zhubao.erp.security.app;

import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AccountAccessProfile;
import com.zhubao.erp.security.domain.AuthAccountOptions;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalEvidenceSummary;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuthUserAccountManagementService {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong auditSequence = new AtomicLong();
    private final AuthUserAccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final PasswordExpiryPolicy passwordExpiryPolicy;
    private final AuthAuditRepository authAuditRepository;
    private final AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository;
    private final AuthSessionService authSessionService;
    private final DocumentManagementRepository documentManagementRepository;
    private final CustomerAuthorizationService customerAuthorizationService;
    private final AuthAccountActionRepository actionRepository;

    public AuthUserAccountManagementService(AuthUserAccountRepository accounts, PasswordHasher hasher, PasswordPolicy policy,
                                            PasswordExpiryPolicy expiry, AuthAuditRepository audits) {
        this(accounts, hasher, policy, expiry, audits, null, null, null, null, null);
    }

    @Autowired
    public AuthUserAccountManagementService(AuthUserAccountRepository accountRepository, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, PasswordExpiryPolicy passwordExpiryPolicy, AuthAuditRepository authAuditRepository,
            AuthAccountPreApprovalApplicationRepository preApprovalApplicationRepository, AuthSessionService authSessionService,
            DocumentManagementRepository documentManagementRepository, CustomerAuthorizationService customerAuthorizationService,
            AuthAccountActionRepository actionRepository) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordExpiryPolicy = passwordExpiryPolicy;
        this.authAuditRepository = authAuditRepository;
        this.preApprovalApplicationRepository = preApprovalApplicationRepository;
        this.authSessionService = authSessionService;
        this.documentManagementRepository = documentManagementRepository;
        this.customerAuthorizationService = customerAuthorizationService;
        this.actionRepository = actionRepository != null ? actionRepository : new InMemoryAuthAccountActionRepository(
                accountRepository, authAuditRepository, customerAuthorizationService == null ? null : customerAuthorizationService.repository(), null);
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
                .filter(role -> role != RoleCode.CUSTOMER_PORTAL)
                .map(role -> new AuthAccountOptions.RoleOption(role.name(), roleLabel(role),
                        permissionGuard.allowedPermissionsFor(role).stream()
                                .sorted()
                                .map(permission -> new AuthAccountOptions.PermissionOption(permission.name(), permission.label()))
                                .toList()))
                .toList());
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

    public AuthUserAccountSummary upsert(ManageAuthUserAccountCommand command, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        String username = normalize(command.username());
        RoleCode role = parseRole(command.role());
        DataScope dataScope = parseDataScope(command.dataScope());
        AuthUserAccount existing = accountRepository.findByUsername(username).orElse(null);
        rejectCustomerPortal(role, existing);
        List<String> customerScope = customerScopeFor(dataScope, command.customerScope(), existing);
        boolean passwordProvided = command.password() != null && !command.password().isBlank();
        List<String> allowedProcesses = command.allowedProcesses() == null
                ? (existing == null ? List.of() : existing.allowedProcesses())
                : WorkshopProcesses.bind(command.allowedProcesses());
        List<String> coverDuties = resolveCoverDuties(role, command.coverDuties(), existing);
        String teamCode = command.teamCode() == null ? (existing == null ? "" : existing.teamCode()) : command.teamCode();
        String departmentCode = command.departmentCode() == null
                ? (existing == null ? "" : existing.departmentCode()) : command.departmentCode();
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
        boolean passwordChangeRequired = passwordProvided || existing.passwordChangeRequired();
        boolean temporaryPasswordConsumed = passwordProvided ? false : existing.temporaryPasswordConsumed();

        AuthUserAccount proposed = new AuthUserAccount(
                username,
                passwordHash,
                command.operator().trim(),
                role,
                dataScope,
                command.enabled(),
                false,
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
                + passwordChangeRequiredSuffix(passwordProvided, passwordChangeRequired);
        AuthAccountMutation mutation = AuthAccountMutation.save(
                proposed,
                newManagementAudit(username, operatorContext, auditedAction)
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

    public AuthUserAccountSummary updateEnabled(
            String username,
            boolean enabled,
            String reason,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        rejectCustomerPortal(account.role(), account);
        String action = (enabled ? "启用账号" : "停用账号")
                + reasonSuffix(reason);
        String transactionalReason = action + (enabled || authSessionService == null ? "" : "；会话吊销在提交后执行");
        actionRepository.commit(AuthAccountMutation.setEnabled(
                account.username(),
                enabled,
                newManagementAudit(account.username(), operatorContext, transactionalReason)
        ));
        AuthUserAccount updated = requireAccount(account.username());
        revokeAfterCommit(updated, enabled, operatorContext);
        return summary(updated, Instant.now());
    }

    public AuthUserAccountSummary unlock(
            String username,
            String reason,
            OperatorContext operatorContext
    ) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        rejectCustomerPortal(account.role(), account);
        String action = "管理员解锁账号" + reasonSuffix(reason);
        actionRepository.commit(AuthAccountMutation.unlock(
                account.username(),
                newManagementAudit(account.username(), operatorContext, action)
        ));
        AuthUserAccount updated = requireAccount(account.username());
        return summary(updated, Instant.now());
    }

    public void delete(String username, OperatorContext operatorContext) {
        requireAdmin(operatorContext);
        AuthUserAccount account = requireAccount(username);
        rejectCustomerPortal(account.role(), account);
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
     * 账号已经停用后，只重试会话吊销，不再次修改账号。
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
            throw new BusinessException("会话吊销仍然失败。请稍后再只重试会话吊销，不要再次修改账号：" + account.username());
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
            throw new BusinessException("账号已停用，会话吊销失败。请只重试会话吊销，不要再次修改账号：" + account.username());
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

    private List<EvidenceAttachment> accountPreApprovalAttachments() {
        if (documentManagementRepository == null) {
            return List.of();
        }
        return documentManagementRepository.attachments().stream()
                .filter(attachment -> "账号前置审批".equals(attachment.businessType()))
                .toList();
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
                    "历史证据缺少：" + requiredEvidence + "，businessType=账号前置审批，businessCode="
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
                .orElse("保留历史账号申请附件");

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

    private String passwordChangeRequiredSuffix(boolean passwordProvided, boolean passwordChangeRequired) {
        return passwordProvided ? "; passwordChangeRequired=" + passwordChangeRequired : "";
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
            case CUSTOMER_PORTAL -> "客户只读";
        };
    }

    /** 客户入口身份单独绑定和停用，不能从内部账号管理改角色、范围、顶岗或口令。 */
    private static void rejectCustomerPortal(RoleCode role, AuthUserAccount existing) {
        if (role == RoleCode.CUSTOMER_PORTAL || (existing != null && existing.role() == RoleCode.CUSTOMER_PORTAL)) {
            throw new BusinessException("客户身份不能通过内部账号管理办理");
        }
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

}
