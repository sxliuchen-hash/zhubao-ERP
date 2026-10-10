package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditIntegrity;
import com.zhubao.erp.security.domain.AuthAuditIntegritySummary;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuthSessionService {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final AtomicLong auditSequence = new AtomicLong();
    private final NavigationMenuService navigationMenuService;
    private final AuthAuditRepository authAuditRepository;
    private final AuthUserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordExpiryPolicy passwordExpiryPolicy;
    private final PasswordPolicy passwordPolicy;
    private final CustomerAuthorizationService customerAuthorizationService;

    public AuthSessionService(
            NavigationMenuService navigationMenuService,
            AuthAuditRepository authAuditRepository,
            AuthUserAccountRepository userAccountRepository,
            PasswordHasher passwordHasher,
            PasswordExpiryPolicy passwordExpiryPolicy,
            PasswordPolicy passwordPolicy
    ) {
        this(navigationMenuService, authAuditRepository, userAccountRepository,
                passwordHasher, passwordExpiryPolicy, passwordPolicy, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthSessionService(
            NavigationMenuService navigationMenuService,
            AuthAuditRepository authAuditRepository,
            AuthUserAccountRepository userAccountRepository,
            PasswordHasher passwordHasher,
            PasswordExpiryPolicy passwordExpiryPolicy,
            PasswordPolicy passwordPolicy,
            CustomerAuthorizationService customerAuthorizationService
    ) {
        this.navigationMenuService = navigationMenuService;
        this.authAuditRepository = authAuditRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.passwordExpiryPolicy = passwordExpiryPolicy;
        this.passwordPolicy = passwordPolicy;
        this.customerAuthorizationService = customerAuthorizationService;
    }

    public AuthSession login(AuthLoginCommand command) {
        String username = normalize(command.username());
        Optional<AuthUserAccount> account = userAccountRepository.findByUsername(username);
        Instant now = Instant.now();
        if (account.isPresent() && account.get().enabled() && isLocked(account.get(), now)) {
            String reason = "账号已临时锁定至 " + DISPLAY_TIME.format(account.get().lockedUntil());
            recordAudit("LOGIN", username, account.get().operator(), account.get().role().name(), "DENIED", reason, "");
            throw new PermissionDeniedException("账号已锁定，请稍后再试");
        }
        if (account.isEmpty() || !account.get().enabled()) {
            recordAudit("LOGIN", username, "", "", "DENIED", "用户名或密码错误", "");
            throw new PermissionDeniedException("用户名或密码错误");
        }
        if (!passwordHasher.matches(command.password(), account.get().passwordHash())) {
            recordFailedLogin(account.get(), now);
            throw new PermissionDeniedException("用户名或密码错误");
        }
        if (account.get().passwordChangeRequired() && account.get().temporaryPasswordConsumed()) {
            String reason = "临时密码已使用，请使用首次登录会话自助改密或联系管理员重置密码";
            recordAudit("LOGIN", account.get().username(), account.get().operator(), account.get().role().name(), "DENIED", reason, "");
            throw new PermissionDeniedException("临时密码已使用，请使用首次登录会话自助改密");
        }
        if (passwordExpiryPolicy.isExpired(account.get(), now)) {
            String reason = "密码已超过 " + passwordExpiryPolicy.maxAgeDays() + " 天有效期，请重置密码";
            recordAudit("LOGIN", account.get().username(), account.get().operator(), account.get().role().name(), "DENIED", reason, "");
            throw new PermissionDeniedException("密码已过期，请联系管理员重置密码");
        }
        if (account.get().passwordChangeRequired()
                && !userAccountRepository.consumeTemporaryPassword(account.get().username())) {
            String reason = "临时密码已被其他会话使用，请使用首次登录会话自助改密或联系管理员重置密码";
            recordAudit("LOGIN", account.get().username(), account.get().operator(), account.get().role().name(), "DENIED", reason, "");
            throw new PermissionDeniedException("临时密码已使用，请使用首次登录会话自助改密");
        }

        String token = newToken();
        Instant expiresAt = now.plus(SESSION_TTL);
        SessionEntry entry = new SessionEntry(token, account.get(), expiresAt);
        sessions.put(token, entry);
        userAccountRepository.recordLoginSuccess(account.get().username());
        recordAudit(
                "LOGIN",
                account.get().username(),
                account.get().operator(),
                account.get().role().name(),
                "SUCCESS",
                (account.get().demoAccount() ? "评审账号登录成功" : "生产账号登录成功")
                        + (account.get().passwordChangeRequired() ? "；临时密码已消费，必须先完成自助改密" : ""),
                token
        );
        return toSession(entry);
    }

    private void recordFailedLogin(AuthUserAccount account, Instant now) {
        int currentFailures = account.lockedUntil() != null && account.lockedUntil().isBefore(now)
                ? 0
                : account.failedLoginCount();
        int nextFailures = currentFailures + 1;
        Instant lockedUntil = nextFailures >= MAX_FAILED_LOGIN_ATTEMPTS ? now.plus(LOCKOUT_DURATION) : null;
        userAccountRepository.recordLoginFailure(account.username(), nextFailures, lockedUntil);
        String reason = lockedUntil == null
                ? "用户名或密码错误，失败次数 " + nextFailures + "/" + MAX_FAILED_LOGIN_ATTEMPTS
                : "用户名或密码错误，账号已临时锁定至 " + DISPLAY_TIME.format(lockedUntil);
        recordAudit("LOGIN", account.username(), account.operator(), account.role().name(), "DENIED", reason, "");
    }

    private boolean isLocked(AuthUserAccount account, Instant now) {
        return account.lockedUntil() != null && account.lockedUntil().isAfter(now);
    }

    public Optional<AuthSession> session(String rawAuthorizationHeader) {
        Optional<SessionEntry> entry = resolve(rawAuthorizationHeader);
        entry.ifPresent(value -> recordAudit(
                "SESSION_READ",
                value.user().username(),
                value.user().operator(),
                value.user().role().name(),
                "SUCCESS",
                "读取当前登录会话",
                value.token()
        ));
        return entry.map(this::toSession);
    }

    public Optional<OperatorContext> operatorContext(String rawAuthorizationHeader) {
        Optional<SessionEntry> entry = resolve(rawAuthorizationHeader);
        entry.ifPresent(value -> {
            if (value.user().passwordChangeRequired()) {
                recordAudit(
                        "SESSION_RESOLVE",
                        value.user().username(),
                        value.user().operator(),
                        value.user().role().name(),
                        "DENIED",
                        "临时密码会话必须先完成自助改密",
                        value.token()
                );
                throw new PermissionDeniedException("临时密码会话必须先完成自助改密");
            }
        });
        return entry.map(value -> new OperatorContext(
                value.user().operator(),
                value.user().role(),
                value.user().dataScope(),
                false,
                value.token(),
                effectiveCustomerScope(value.user())
        ));
    }

    /**
     * 按登录会话重新读取账号。岗位工序以账号上的绑定为准，不使用请求里自报的工序列表。
     */
    public Optional<AuthUserAccount> currentAccount(String rawAuthorizationHeader) {
        Optional<SessionEntry> entry = resolve(rawAuthorizationHeader);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        AuthUserAccount sessionUser = entry.get().user();
        if (sessionUser.passwordChangeRequired()) {
            recordAudit(
                    "SESSION_RESOLVE",
                    sessionUser.username(),
                    sessionUser.operator(),
                    sessionUser.role().name(),
                    "DENIED",
                    "临时密码会话必须先完成自助改密",
                    entry.get().token()
            );
            throw new PermissionDeniedException("临时密码会话必须先完成自助改密");
        }
        return userAccountRepository.findByUsername(sessionUser.username());
    }

    private List<String> effectiveCustomerScope(AuthUserAccount user) {
        if (user.dataScope() != DataScope.CUSTOMER_RANGE || customerAuthorizationService == null) {
            return user.customerScope();
        }
        return customerAuthorizationService.effectiveCustomerScope(user.username(), user.customerScope());
    }

    public AuthSession changeOwnPassword(String rawAuthorizationHeader, ChangeOwnPasswordCommand command) {
        SessionEntry entry = resolve(rawAuthorizationHeader)
                .orElseThrow(() -> new PermissionDeniedException("自助改密需要登录会话"));
        AuthUserAccount currentAccount = userAccountRepository.findByUsername(entry.user().username())
                .orElseThrow(() -> new PermissionDeniedException("登录账号不存在或已停用"));
        Instant now = Instant.now();
        if (!currentAccount.enabled()) {
            recordAudit(
                    "PASSWORD_CHANGE",
                    currentAccount.username(),
                    currentAccount.operator(),
                    currentAccount.role().name(),
                    "DENIED",
                    "账号已停用，拒绝自助改密",
                    entry.token()
            );
            throw new PermissionDeniedException("登录账号已停用");
        }
        if (isLocked(currentAccount, now)) {
            recordAudit(
                    "PASSWORD_CHANGE",
                    currentAccount.username(),
                    currentAccount.operator(),
                    currentAccount.role().name(),
                    "DENIED",
                    "账号已临时锁定，拒绝自助改密",
                    entry.token()
            );
            throw new PermissionDeniedException("账号已锁定，请稍后再试");
        }
        if (!passwordHasher.matches(command.currentPassword(), currentAccount.passwordHash())) {
            recordAudit(
                    "PASSWORD_CHANGE",
                    currentAccount.username(),
                    currentAccount.operator(),
                    currentAccount.role().name(),
                    "DENIED",
                    "当前密码错误",
                    entry.token()
            );
            throw new PermissionDeniedException("当前密码错误");
        }
        if (passwordHasher.matches(command.newPassword(), currentAccount.passwordHash())) {
            recordAudit(
                    "PASSWORD_CHANGE",
                    currentAccount.username(),
                    currentAccount.operator(),
                    currentAccount.role().name(),
                    "DENIED",
                    "新密码不能与当前密码相同",
                    entry.token()
            );
            throw new BusinessException("新密码不能与当前密码相同");
        }
        try {
            passwordPolicy.assertAcceptable(command.newPassword(), currentAccount.username());
        } catch (BusinessException ex) {
            recordAudit(
                    "PASSWORD_CHANGE",
                    currentAccount.username(),
                    currentAccount.operator(),
                    currentAccount.role().name(),
                    "DENIED",
                    "新密码不符合策略：" + ex.getMessage(),
                    entry.token()
            );
            throw ex;
        }

        AuthUserAccount updated = userAccountRepository.save(new AuthUserAccount(
                currentAccount.username(),
                passwordHasher.hash(command.newPassword()),
                currentAccount.operator(),
                currentAccount.role(),
                currentAccount.dataScope(),
                currentAccount.enabled(),
                currentAccount.demoAccount(),
                now,
                0,
                null,
                currentAccount.customerScope(),
                false,
                false,
                currentAccount.allowedProcesses()
        ));
        SessionEntry updatedEntry = new SessionEntry(entry.token(), updated, entry.expiresAt());
        sessions.put(entry.token(), updatedEntry);
        recordAudit(
                "PASSWORD_CHANGE",
                updated.username(),
                updated.operator(),
                updated.role().name(),
                "SUCCESS",
                "用户自助修改密码并刷新 passwordChangedAt",
                entry.token()
        );
        return toSession(updatedEntry);
    }

    public void logout(String rawAuthorizationHeader) {
        bearerToken(rawAuthorizationHeader).ifPresent(token -> {
            SessionEntry removed = sessions.remove(token);
            if (removed == null) {
                recordAudit("LOGOUT", "", "", "", "DENIED", "登录会话不存在或已退出", token);
                return;
            }
            recordAudit(
                    "LOGOUT",
                    removed.user().username(),
                    removed.user().operator(),
                    removed.user().role().name(),
                    "SUCCESS",
                    "退出登录会话",
                    token
            );
        });
    }

    public int revokeSessionsForUsername(String username, String reason) {
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            return 0;
        }
        List<SessionEntry> revoked = sessions.values().stream()
                .filter(entry -> normalize(entry.user().username()).equals(normalizedUsername))
                .toList();
        int revokedCount = 0;
        for (SessionEntry entry : revoked) {
            if (sessions.remove(entry.token(), entry)) {
                recordAudit(
                        "SESSION_REVOKED",
                        entry.user().username(),
                        entry.user().operator(),
                        entry.user().role().name(),
                        "SUCCESS",
                        reason == null || reason.isBlank() ? "Account session revoked" : reason.trim(),
                        entry.token()
                );
                revokedCount++;
            }
        }
        return revokedCount;
    }

    public List<String> demoUsernames() {
        return userAccountRepository.findActiveUsernames();
    }

    public List<AuthAuditEvent> auditEvents(int limit) {
        return authAuditRepository.findRecent(limit);
    }

    public AuthAuditIntegritySummary auditIntegrity(int limit) {
        List<AuthAuditEvent> events = authAuditRepository.findRecent(limit);
        if (events.isEmpty()) {
            return new AuthAuditIntegritySummary(0, true, "", "", "", "暂无认证审计事件");
        }
        for (int index = 0; index < events.size(); index++) {
            AuthAuditEvent current = events.get(index);
            if (!AuthAuditIntegrity.matches(current)) {
                return new AuthAuditIntegritySummary(
                        events.size(),
                        false,
                        events.get(0).eventId(),
                        events.get(0).eventHash(),
                        current.eventId(),
                        "审计事件摘要不匹配"
                );
            }
            if (index + 1 < events.size()) {
                AuthAuditEvent previous = events.get(index + 1);
                if (!AuthAuditIntegrity.normalize(current.previousEventHash()).equals(AuthAuditIntegrity.normalize(previous.eventHash()))) {
                    return new AuthAuditIntegritySummary(
                            events.size(),
                            false,
                            events.get(0).eventId(),
                            events.get(0).eventHash(),
                            current.eventId(),
                            "审计事件前序摘要不连续"
                    );
                }
            }
        }
        AuthAuditEvent latest = events.get(0);
        return new AuthAuditIntegritySummary(
                events.size(),
                true,
                latest.eventId(),
                latest.eventHash(),
                "",
                "最近认证审计摘要链校验通过"
        );
    }

    private Optional<SessionEntry> resolve(String rawAuthorizationHeader) {
        Optional<String> token = bearerToken(rawAuthorizationHeader);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        SessionEntry entry = sessions.get(token.get());
        if (entry == null) {
            recordAudit("SESSION_RESOLVE", "", "", "", "DENIED", "登录会话不存在或已退出", token.get());
            throw new PermissionDeniedException("登录会话不存在或已退出");
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            sessions.remove(entry.token());
            recordAudit(
                    "SESSION_RESOLVE",
                    entry.user().username(),
                    entry.user().operator(),
                    entry.user().role().name(),
                    "DENIED",
                    "登录会话已过期",
                    entry.token()
            );
            throw new PermissionDeniedException("登录会话已过期");
        }
        return Optional.of(entry);
    }

    private AuthSession toSession(SessionEntry entry) {
        AuthUserAccount user = entry.user();
        return new AuthSession(
                entry.token(),
                user.username(),
                user.operator(),
                user.role().name(),
                roleLabel(user.role()),
                user.dataScope().name(),
                dataScopeLabel(user.dataScope()),
                DISPLAY_TIME.format(entry.expiresAt()),
                user.demoAccount(),
                displayInstant(user.passwordChangedAt()),
                DISPLAY_TIME.format(passwordExpiryPolicy.expiresAt(user)),
                passwordExpiryPolicy.isExpired(user, Instant.now()),
                user.passwordChangeRequired(),
                user.customerScope(),
                navigationMenuService.menuCodesFor(user.role()),
                navigationMenuService.menuTreeFor(user.role()),
                user.allowedProcesses()
        );
    }

    private Optional<String> bearerToken(String rawAuthorizationHeader) {
        if (rawAuthorizationHeader == null || rawAuthorizationHeader.isBlank()) {
            return Optional.empty();
        }
        String value = rawAuthorizationHeader.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new PermissionDeniedException("Authorization 只支持 Bearer token");
        }
        String token = value.substring(7).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void recordAudit(
            String eventType,
            String username,
            String operator,
            String role,
            String result,
            String reason,
            String token
    ) {
        authAuditRepository.save(new AuthAuditEvent(
                "AUTH-AUD-" + DISPLAY_TIME.format(Instant.now()).replace(" ", "-").replace(":", "") + "-"
                        + String.format("%04d", auditSequence.incrementAndGet()),
                eventType,
                username,
                operator,
                role,
                result,
                reason,
                DISPLAY_TIME.format(Instant.now()),
                tokenSuffix(token)
        ));
    }

    private String tokenSuffix(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        int start = Math.max(0, token.length() - 6);
        return token.substring(start);
    }

    private String displayInstant(Instant value) {
        return value == null ? "" : DISPLAY_TIME.format(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

    private record SessionEntry(
            String token,
            AuthUserAccount user,
            Instant expiresAt
    ) {
    }
}
