package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final AuthAuditRepository authAuditRepository;

    public AuthUserAccountManagementService(
            AuthUserAccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            AuthAuditRepository authAuditRepository
    ) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.authAuditRepository = authAuditRepository;
    }

    public List<AuthUserAccountSummary> accounts() {
        Instant now = Instant.now();
        return accountRepository.findAll().stream()
                .map(account -> summary(account, now))
                .toList();
    }

    public AuthUserAccountSummary upsert(ManageAuthUserAccountCommand command, OperatorContext operatorContext) {
        String username = normalize(command.username());
        RoleCode role = parseRole(command.role());
        DataScope dataScope = parseDataScope(command.dataScope());
        AuthUserAccount existing = accountRepository.findByUsername(username).orElse(null);
        boolean passwordProvided = command.password() != null && !command.password().isBlank();
        if (existing == null && !passwordProvided) {
            throw new BusinessException("新建账号必须提供初始密码");
        }
        if (passwordProvided) {
            passwordPolicy.assertAcceptable(command.password(), username);
        }
        String passwordHash = passwordProvided ? passwordHasher.hash(command.password()) : existing.passwordHash();
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
                failedLoginCount,
                lockedUntil
        ));
        String action = existing == null ? "创建账号" : passwordProvided ? "更新账号并重置密码" : "更新账号";
        recordManagementAudit(username, operatorContext, action);
        return summary(saved, Instant.now());
    }

    public AuthUserAccountSummary updateEnabled(String username, boolean enabled, String reason, OperatorContext operatorContext) {
        AuthUserAccount account = requireAccount(username);
        accountRepository.updateEnabled(account.username(), enabled);
        AuthUserAccount updated = requireAccount(account.username());
        recordManagementAudit(account.username(), operatorContext, (enabled ? "启用账号" : "停用账号") + reasonSuffix(reason));
        return summary(updated, Instant.now());
    }

    public AuthUserAccountSummary unlock(String username, String reason, OperatorContext operatorContext) {
        AuthUserAccount account = requireAccount(username);
        accountRepository.unlock(account.username());
        AuthUserAccount updated = requireAccount(account.username());
        recordManagementAudit(account.username(), operatorContext, "管理员解锁账号" + reasonSuffix(reason));
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
                account.enabled(),
                account.demoAccount(),
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : "；原因：" + reason.trim();
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
}
