package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.error.SessionInvalidException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSessionServiceTest {

    private final NavigationMenuService navigationMenuService =
            new NavigationMenuService(new com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository());
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void demoUsernamesExcludesProductionAndDisabledAccounts() {
        InMemoryAuthUserAccountRepository repository = new InMemoryAuthUserAccountRepository(passwordHasher);
        repository.save(new AuthUserAccount(
                "trial-admin", "unused-hash", "试用管理员", RoleCode.ADMIN, DataScope.ALL, true, false
        ));
        repository.updateEnabled("leader", false);
        AuthSessionService service = service(repository);

        assertThat(service.demoUsernames())
                .containsExactly("admin", "carve", "inlay", "operator", "polish", "qc", "warehouse");

        repository.findAll().stream().filter(AuthUserAccount::demoAccount)
                .forEach(account -> repository.updateEnabled(account.username(), false));
        assertThat(service.demoUsernames()).isEmpty();
    }

    @Test
    void logsInDemoUserAndResolvesOperatorContextFromBearerToken() {
        AuthSessionService service = service();

        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));
        OperatorContext context = service.operatorContext("Bearer " + session.token()).orElseThrow();

        assertThat(session.username()).isEqualTo("leader");
        assertThat(session.operator()).isEqualTo("车间组长 马杰");
        assertThat(session.role()).isEqualTo("WORKSHOP_LEADER");
        assertThat(session.menuCodes()).contains("production", "quality", "approvals", "sample");
        assertThat(session.menuTree())
                .anySatisfy(item -> {
                    assertThat(item.code()).isEqualTo("sample");
                    assertThat(item.buttonCodes()).contains("REGISTER_IMPORT_REVIEW");
                });
        assertThat(context.operator()).isEqualTo("车间组长 马杰");
        assertThat(context.role()).isEqualTo(RoleCode.WORKSHOP_LEADER);
        assertThat(context.headerBased()).isFalse();
        assertThat(context.sessionToken()).isEqualTo(session.token());
    }

    @Test
    void rejectsWrongPasswordAndInvalidAuthorizationScheme() {
        AuthSessionService service = service();

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "wrong")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");

        assertThatThrownBy(() -> service.operatorContext("Basic bad-token"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Bearer token");
    }

    @Test
    void logoutInvalidatesSessionToken() {
        AuthSessionService service = service();
        AuthSession session = service.login(new AuthLoginCommand("operator", "operator123"));

        service.logout("Bearer " + session.token());

        assertThatThrownBy(() -> service.session("Bearer " + session.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE);
    }

    @Test
    void revokesAllSessionsForUsername() {
        AuthSessionService service = service();
        AuthSession first = service.login(new AuthLoginCommand("operator", "operator123"));
        AuthSession second = service.login(new AuthLoginCommand("operator", "operator123"));
        AuthSession leader = service.login(new AuthLoginCommand("leader", "leader123"));

        int revoked = service.revokeSessionsForUsername("operator", "account disabled");

        assertThat(revoked).isEqualTo(2);
        assertThatThrownBy(() -> service.session("Bearer " + first.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE);
        assertThatThrownBy(() -> service.session("Bearer " + second.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE);
        assertThat(service.session("Bearer " + leader.token())).isPresent();
        assertThat(service.auditEvents(10))
                .filteredOn(event -> event.eventType().equals("SESSION_REVOKED"))
                .hasSize(2)
                .allSatisfy(event -> {
                    assertThat(event.username()).isEqualTo("operator");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.reason()).isEqualTo("account disabled");
                    assertThat(event.sessionTokenSuffix()).isNotEmpty();
                });
    }

    @Test
    void recordsLoginSessionAndLogoutAuditEventsWithoutExposingFullToken() {
        AuthSessionService service = service();

        AuthSession session = service.login(new AuthLoginCommand("warehouse", "warehouse123"));
        service.session("Bearer " + session.token());
        service.logout("Bearer " + session.token());

        assertThat(service.auditEvents(10))
                .extracting("eventType")
                .contains("LOGIN", "SESSION_READ", "LOGOUT");
        assertThat(service.auditEvents(10))
                .allSatisfy(event -> assertThat(event.eventHash()).hasSize(64));
        assertThat(service.auditEvents(10).get(0).previousEventHash())
                .isEqualTo(service.auditEvents(10).get(1).eventHash());
        assertThat(service.auditIntegrity(10))
                .satisfies(summary -> {
                    assertThat(summary.verified()).isTrue();
                    assertThat(summary.checkedEvents()).isGreaterThanOrEqualTo(3);
                    assertThat(summary.latestEventHash()).hasSize(64);
                    assertThat(summary.brokenEventId()).isEmpty();
                });
        assertThat(service.auditEvents(10))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("warehouse");
                    assertThat(event.operator()).isEqualTo("库管员 何琳");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.sessionTokenSuffix()).isNotEmpty();
                    assertThat(session.token()).endsWith(event.sessionTokenSuffix());
                    assertThat(event.sessionTokenSuffix()).isNotEqualTo(session.token());
                });
    }

    @Test
    void recordsFailedLoginAuditEvent() {
        AuthSessionService service = service();

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "bad-password")))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(service.auditEvents(5))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("leader");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.reason()).contains("用户名或密码错误");
                    assertThat(event.sessionTokenSuffix()).isEmpty();
                });
    }

    @Test
    void exposesSortedDemoUsernames() {
        AuthSessionService service = service();

        assertThat(service.demoUsernames()).containsExactly(
                "admin", "carve", "inlay", "leader", "operator", "polish", "qc", "warehouse");
    }

    @Test
    void logsInRepositoryBackedProductionAccountWithPasswordHash() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        userRepository.save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("planner-strong-pass"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false
        ));
        AuthSessionService service = service(userRepository);

        AuthSession session = service.login(new AuthLoginCommand("planner", "planner-strong-pass"));

        assertThat(session.username()).isEqualTo("planner");
        assertThat(session.operator()).isEqualTo("计划员 陈敏");
        assertThat(session.role()).isEqualTo("SALES");
        assertThat(session.demoAccount()).isFalse();
        assertThat(session.menuCodes()).contains("inbound", "inventory", "sample");
    }

    @Test
    void temporaryPasswordSessionMustChangePasswordBeforeResolvingOperatorContext() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        userRepository.save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("Secure-2026-Temp!"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                java.time.Instant.now(),
                0,
                null,
                java.util.List.of("恒信珠宝"),
                true
        ));
        AuthSessionService service = service(userRepository);

        AuthSession session = service.login(new AuthLoginCommand("planner", "Secure-2026-Temp!"));

        assertThat(session.passwordChangeRequired()).isTrue();
        assertThat(service.session("Bearer " + session.token())).isPresent();
        assertThat(userRepository.findByUsername("planner"))
                .get()
                .satisfies(account -> {
                    assertThat(account.passwordChangeRequired()).isTrue();
                    assertThat(account.temporaryPasswordConsumed()).isTrue();
                    assertThat(account.failedLoginCount()).isZero();
                });
        assertThatThrownBy(() -> service.login(new AuthLoginCommand("planner", "Secure-2026-Temp!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("临时密码已使用");
        assertThat(userRepository.findByUsername("planner"))
                .get()
                .extracting(AuthUserAccount::failedLoginCount)
                .isEqualTo(0);
        assertThatThrownBy(() -> service.operatorContext("Bearer " + session.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("临时密码会话必须先完成自助改密");

        AuthSession changed = service.changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("Secure-2026-Temp!", "Secure-2026-New!")
        );

        assertThat(changed.passwordChangeRequired()).isFalse();
        assertThat(userRepository.findByUsername("planner"))
                .get()
                .satisfies(account -> {
                    assertThat(account.passwordChangeRequired()).isFalse();
                    assertThat(account.temporaryPasswordConsumed()).isFalse();
                });
        assertThat(service.operatorContext("Bearer " + changed.token())).isPresent();
        assertThat(service.login(new AuthLoginCommand("planner", "Secure-2026-New!")).passwordChangeRequired())
                .isFalse();
    }

    @Test
    void rejectsExpiredProductionPasswordAfterPasswordMatches() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        userRepository.save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("Secure-2026-Expired!"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                java.time.Instant.now().minus(java.time.Duration.ofDays(91)),
                0,
                null
        ));
        AuthSessionService service = service(userRepository);

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("planner", "Secure-2026-Expired!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("密码已过期");
        assertThat(userRepository.findByUsername("planner"))
                .get()
                .extracting(AuthUserAccount::failedLoginCount)
                .isEqualTo(0);
        assertThat(service.auditEvents(5))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("planner");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.reason()).contains("密码已超过 90 天有效期");
                });
    }

    @Test
    void rejectsDisabledUserAccount() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        userRepository.save(new AuthUserAccount(
                "disabled",
                passwordHasher.hash("disabled-pass"),
                "停用账号",
                RoleCode.OPERATOR,
                DataScope.SELF,
                false,
                false
        ));
        AuthSessionService service = service(userRepository);

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("disabled", "disabled-pass")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void locksAccountAfterRepeatedFailedLoginsAndRecordsAuditReason() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService service = service(userRepository);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "bad-password")))
                    .isInstanceOf(PermissionDeniedException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        assertThat(userRepository.findByUsername("leader"))
                .get()
                .satisfies(account -> {
                    assertThat(account.failedLoginCount()).isEqualTo(5);
                    assertThat(account.lockedUntil()).isNotNull();
                });
        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "leader123")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("账号已锁定");
        assertThat(service.auditEvents(10))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN");
                    assertThat(event.username()).isEqualTo("leader");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.reason()).contains("账号已临时锁定");
                });
    }

    @Test
    void successfulLoginClearsFailedLoginCounter() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService service = service(userRepository);

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("qc", "bad-password")))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(userRepository.findByUsername("qc")).get()
                .extracting(AuthUserAccount::failedLoginCount)
                .isEqualTo(1);

        service.login(new AuthLoginCommand("qc", "qc123"));

        assertThat(userRepository.findByUsername("qc"))
                .get()
                .satisfies(account -> {
                    assertThat(account.failedLoginCount()).isZero();
                    assertThat(account.lockedUntil()).isNull();
                });
    }

    @Test
    void changesOwnPasswordWithBearerSessionAndRefreshesExpiryFields() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        java.time.Instant oldChangedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(30));
        userRepository.save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("Secure-2026-Old!"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                oldChangedAt,
                3,
                java.time.Instant.now().plusSeconds(900)
        ));
        AuthSessionService service = service(userRepository);
        userRepository.unlock("planner");
        AuthSession session = service.login(new AuthLoginCommand("planner", "Secure-2026-Old!"));

        AuthSession changed = service.changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("Secure-2026-Old!", "Secure-2026-New!")
        );

        assertThat(changed.token()).isEqualTo(session.token());
        assertThat(changed.passwordChangedAt()).isNotEqualTo(session.passwordChangedAt());
        assertThat(changed.passwordExpired()).isFalse();
        assertThat(userRepository.findByUsername("planner"))
                .get()
                .satisfies(account -> {
                    assertThat(passwordHasher.matches("Secure-2026-New!", account.passwordHash())).isTrue();
                    assertThat(account.passwordChangedAt()).isAfter(oldChangedAt);
                    assertThat(account.failedLoginCount()).isZero();
                    assertThat(account.lockedUntil()).isNull();
                });
        assertThatThrownBy(() -> service.login(new AuthLoginCommand("planner", "Secure-2026-Old!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(service.login(new AuthLoginCommand("planner", "Secure-2026-New!")).username()).isEqualTo("planner");
        assertThat(service.auditEvents(10))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGE");
                    assertThat(event.username()).isEqualTo("planner");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.reason()).contains("自助修改密码");
                });
    }

    @Test
    void ownPasswordChangeRequiresBearerSessionAndRejectsBadCurrentPassword() {
        InMemoryAuthUserAccountRepository userRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService service = service(userRepository);
        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));

        assertThatThrownBy(() -> service.changeOwnPassword(
                null,
                new ChangeOwnPasswordCommand("leader123", "Secure-2026-Leader!")
        ))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("自助改密需要登录会话");
        assertThatThrownBy(() -> service.changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("wrong", "Secure-2026-Leader!")
        ))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("当前密码错误");
        assertThat(userRepository.findByUsername("leader"))
                .get()
                .extracting(AuthUserAccount::failedLoginCount)
                .isEqualTo(0);
        assertThat(service.auditEvents(10))
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo("PASSWORD_CHANGE");
                    assertThat(event.username()).isEqualTo("leader");
                    assertThat(event.result()).isEqualTo("DENIED");
                    assertThat(event.reason()).contains("当前密码错误");
                });
    }

    @Test
    void ownPasswordChangeRejectsSameOrWeakNewPassword() {
        AuthSessionService service = service();
        AuthSession session = service.login(new AuthLoginCommand("leader", "leader123"));

        assertThatThrownBy(() -> service.changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("leader123", "leader123")
        ))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class)
                .hasMessageContaining("新密码不能与当前密码相同");
        assertThatThrownBy(() -> service.changeOwnPassword(
                "Bearer " + session.token(),
                new ChangeOwnPasswordCommand("leader123", "short1")
        ))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class)
                .hasMessageContaining("密码长度至少 12 位");
    }

    @Test
    void expiredRevokedAndChangedSessionsUseTheSameClientContract() throws Exception {
        InMemoryAuthUserAccountRepository repository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService service = service(repository);
        AuthSession expired = service.login(new AuthLoginCommand("operator", "operator123"));
        expire(service, expired.token());
        assertSessionInvalid(() -> service.session("Bearer " + expired.token()));
        assertThat(service.auditEvents(5)).anySatisfy(event -> assertThat(event.reason()).isEqualTo("登录会话已过期"));

        AuthSession revoked = service.login(new AuthLoginCommand("operator", "operator123"));
        assertThat(service.revokeSessionsForUsername("operator", "account disabled")).isEqualTo(1);
        assertSessionInvalid(() -> service.session("Bearer " + revoked.token()));

        AuthSession changed = service.login(new AuthLoginCommand("leader", "leader123"));
        AuthUserAccount leader = repository.findByUsername("leader").orElseThrow();
        repository.save(new AuthUserAccount(
                leader.username(), leader.passwordHash(), leader.operator(), RoleCode.ADMIN, leader.dataScope(),
                true, leader.demoAccount(), leader.passwordChangedAt(), leader.failedLoginCount(), leader.lockedUntil(),
                leader.customerScope(), leader.passwordChangeRequired(), leader.temporaryPasswordConsumed(),
                leader.allowedProcesses()
        ));
        assertSessionInvalid(() -> service.session("Bearer " + changed.token()));
        assertThat(service.auditEvents(20)).anySatisfy(event -> assertThat(event.reason()).contains("账号密码或权限已变更"));

        assertThatThrownBy(() -> service.login(new AuthLoginCommand("leader", "wrong")))
                .isInstanceOf(PermissionDeniedException.class)
                .isNotInstanceOf(SessionInvalidException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    private void assertSessionInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(SessionInvalidException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE)
                .satisfies(error -> assertThat(((SessionInvalidException) error).code()).isEqualTo(40301));
    }

    @SuppressWarnings("unchecked")
    private void expire(AuthSessionService service, String token) throws Exception {
        Field sessionsField = AuthSessionService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        ConcurrentHashMap<String, Object> sessions = (ConcurrentHashMap<String, Object>) sessionsField.get(service);
        Object entry = sessions.get(token);
        Class<?> type = entry.getClass();
        var constructor = type.getDeclaredConstructor(String.class, AuthUserAccount.class, Instant.class);
        constructor.setAccessible(true);
        Object user = type.getDeclaredMethod("user").invoke(entry);
        sessions.put(token, constructor.newInstance(token, user, Instant.EPOCH));
    }

    private AuthSessionService service() {
        return service(new InMemoryAuthUserAccountRepository(passwordHasher));
    }

    @Test
    void 会话吊销未执行时也拒绝权限变更和同名重建的旧令牌() {
        InMemoryAuthUserAccountRepository repository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthSessionService service = service(repository);
        AuthSession first = service.login(new AuthLoginCommand("operator", "operator123"));
        repository.save(new AuthUserAccount("operator", passwordHasher.hash("operator123"), "操作工",
                RoleCode.SALES, DataScope.ALL, true, true));
        assertThatThrownBy(() -> service.operatorContext("Bearer " + first.token()))
                .isInstanceOf(SessionInvalidException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE);
        AuthSession second = service.login(new AuthLoginCommand("operator", "operator123"));
        repository.delete("operator");
        repository.save(new AuthUserAccount("operator", passwordHasher.hash("operator123"), "新操作工",
                RoleCode.SALES, DataScope.ALL, true, true));
        assertThatThrownBy(() -> service.session("Bearer " + second.token()))
                .isInstanceOf(SessionInvalidException.class)
                .hasMessage(SessionInvalidException.CLIENT_MESSAGE);
    }

    private AuthSessionService service(AuthUserAccountRepository userRepository) {
        return new AuthSessionService(
                navigationMenuService,
                new InMemoryAuthAuditRepository(),
                userRepository,
                passwordHasher,
                passwordExpiryPolicy,
                passwordPolicy
        );
    }
}
