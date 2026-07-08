package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthSession;
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
import java.util.Map;
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
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, DemoUser> demoUsers = Map.of(
            "admin", new DemoUser("admin", "admin123", "系统管理员", RoleCode.ADMIN, DataScope.ALL),
            "leader", new DemoUser("leader", "leader123", "车间组长 马杰", RoleCode.WORKSHOP_LEADER, DataScope.TEAM),
            "qc", new DemoUser("qc", "qc123", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.DEPARTMENT),
            "warehouse", new DemoUser("warehouse", "warehouse123", "库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT),
            "operator", new DemoUser("operator", "operator123", "操作工 王强", RoleCode.OPERATOR, DataScope.SELF)
    );
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final AtomicLong auditSequence = new AtomicLong();
    private final NavigationMenuService navigationMenuService;
    private final AuthAuditRepository authAuditRepository;

    public AuthSessionService(
            NavigationMenuService navigationMenuService,
            AuthAuditRepository authAuditRepository
    ) {
        this.navigationMenuService = navigationMenuService;
        this.authAuditRepository = authAuditRepository;
    }

    public AuthSession login(AuthLoginCommand command) {
        String username = normalize(command.username());
        DemoUser user = demoUsers.get(username);
        if (user == null || !user.password().equals(command.password())) {
            recordAudit("LOGIN", username, "", "", "DENIED", "用户名或密码错误", "");
            throw new PermissionDeniedException("用户名或密码错误");
        }

        String token = newToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        SessionEntry entry = new SessionEntry(token, user, expiresAt);
        sessions.put(token, entry);
        recordAudit("LOGIN", user.username(), user.operator(), user.role().name(), "SUCCESS", "演示账号登录成功", token);
        return toSession(entry);
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
        return resolve(rawAuthorizationHeader)
                .map(entry -> new OperatorContext(
                        entry.user().operator(),
                        entry.user().role(),
                        entry.user().dataScope(),
                        false,
                        entry.token()
                ));
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

    public List<String> demoUsernames() {
        return demoUsers.keySet().stream().sorted().toList();
    }

    public List<AuthAuditEvent> auditEvents(int limit) {
        return authAuditRepository.findRecent(limit);
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
        DemoUser user = entry.user();
        return new AuthSession(
                entry.token(),
                user.username(),
                user.operator(),
                user.role().name(),
                roleLabel(user.role()),
                user.dataScope().name(),
                dataScopeLabel(user.dataScope()),
                DISPLAY_TIME.format(entry.expiresAt()),
                true,
                navigationMenuService.menuCodesFor(user.role()),
                navigationMenuService.menuTreeFor(user.role())
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

    private record DemoUser(
            String username,
            String password,
            String operator,
            RoleCode role,
            DataScope dataScope
    ) {
    }

    private record SessionEntry(
            String token,
            DemoUser user,
            Instant expiresAt
    ) {
    }
}
