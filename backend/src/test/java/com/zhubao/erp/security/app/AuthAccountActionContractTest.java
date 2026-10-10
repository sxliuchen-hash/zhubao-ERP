package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAccountActionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import com.zhubao.erp.security.infra.JdbcAuthAccountActionRepository;
import com.zhubao.erp.security.infra.JdbcAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.JdbcAuthAuditRepository;
import com.zhubao.erp.security.infra.JdbcAuthUserAccountRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内存与 H2 MySQL 兼容模式共用的账号动作行为。H2 只验证事务状态机，不是真实 MySQL。
 * 修复前同一可执行申请在两次读取对齐后会执行两次：statusWrites=2，managementAudits=2。
 * 审计失败时账号已停用且会话已吊销，申请仍是 READY_FOR_BLOCKING_APPROVAL。
 * 申请状态保存失败时账号已停用且已有 1 条管理审计，申请仍可执行。
 */
class AuthAccountActionContractTest {

    @Test
    void memoryAndJdbcShareCommitBehavior() throws Exception {
        assertSharedBehavior(memoryHarness());
        assertSharedBehavior(jdbcHarness());
    }

    @Test
    void serviceRejectsOverlappingExecutionAndRetriesAfterRollback() throws Exception {
        assertServiceOverlap(memoryServiceHarness());
        assertServiceOverlap(jdbcServiceHarness());
    }

    @Test
    void failedSessionRevocationStaysDisabledAndCanBeRetried() {
        InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        InMemoryAuthAuditRepository audits = new InMemoryAuthAuditRepository();
        InMemoryAuthAccountPreApprovalApplicationRepository applications =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        applications.save(disableApplication("APP-REVOKE", "operator"));
        FlakySessions sessions = new FlakySessions(accounts, audits);
        sessions.fail = true;
        AuthSession login = sessions.login(new AuthLoginCommand("operator", "operator123"));
        AuthUserAccountManagementService service = new AuthUserAccountManagementService(
                accounts,
                new PasswordHasher(),
                new PasswordPolicy(),
                new PasswordExpiryPolicy(),
                audits,
                null,
                applications,
                sessions
        );
        OperatorContext admin = admin();

        assertThatThrownBy(() -> service.updateEnabled("operator", false, "吊销失败", "APP-REVOKE", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只重试会话吊销");
        assertThat(accounts.findByUsername("operator").orElseThrow().enabled()).isFalse();
        assertThat(applications.findByCode("APP-REVOKE").orElseThrow().applicationStatus()).isEqualTo("EXECUTED");
        assertThatThrownBy(() -> service.updateEnabled("operator", false, "再次执行", "APP-REVOKE", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未具备账号动作放行条件");
        assertThatThrownBy(() -> sessions.session("Bearer " + login.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录账号已停用");

        AuthSession stillThere = sessions.login(new AuthLoginCommand("leader", "leader123"));
        applications.save(disableApplication("APP-REVOKE-LEADER", "leader"));
        sessions.fail = true;
        assertThatThrownBy(() -> service.updateEnabled("leader", false, "吊销失败", "APP-REVOKE-LEADER", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只重试会话吊销");
        assertThatThrownBy(() -> service.retrySessionRevocation("leader", admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍然失败");
        assertThat(applications.findByCode("APP-REVOKE-LEADER").orElseThrow().applicationStatus()).isEqualTo("EXECUTED");
        sessions.fail = false;
        assertThat(service.retrySessionRevocation("leader", admin).revokedSessions()).isEqualTo(1);
        assertThatThrownBy(() -> sessions.session("Bearer " + stillThere.token()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("登录会话不存在或已退出");
    }

    private void assertSharedBehavior(Harness harness) throws Exception {
        harness.reset("operator");
        harness.saveApplication(disableApplication("APP-REPEAT"));
        long repeatBefore = harness.managementAudits("operator");
        harness.actions().commit(disableMutation("operator", "APP-REPEAT", "audit-repeat-1"));
        assertThatThrownBy(() -> harness.actions().commit(disableMutation("operator", "APP-REPEAT", "audit-repeat-2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未具备账号动作放行条件");
        assertThat(harness.enabled("operator")).isFalse();
        assertThat(harness.managementAudits("operator")).isEqualTo(repeatBefore + 1);
        assertThat(harness.applicationStatus("APP-REPEAT")).isEqualTo("EXECUTED");

        harness.reset("operator");
        for (String point : List.of("account", "audit", "application")) {
            String code = "APP-" + point.toUpperCase();
            harness.saveApplication(disableApplication(code));
            long before = harness.managementAudits("operator");
            harness.failAt(point);
            assertThatThrownBy(() -> harness.actions().commit(disableMutation("operator", code, "audit-" + point)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(harness.enabled("operator")).isTrue();
            assertThat(harness.applicationStatus(code)).isEqualTo("READY_FOR_BLOCKING_APPROVAL");
            assertThat(harness.managementAudits("operator")).isEqualTo(before);
            harness.failAt("none");
            harness.actions().commit(disableMutation("operator", code, "audit-" + point + "-retry"));
            assertThat(harness.enabled("operator")).isFalse();
            assertThat(harness.applicationStatus(code)).isEqualTo("EXECUTED");
            assertThat(harness.managementAudits("operator")).isEqualTo(before + 1);
            harness.reset("operator");
        }

        harness.saveApplication(disableApplication("APP-RACE"));
        long raceBefore = harness.managementAudits("operator");
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> runCommit(harness, "APP-RACE", "race-a", success, rejected));
            Future<?> second = pool.submit(() -> runCommit(harness, "APP-RACE", "race-b", success, rejected));
            first.get(8, TimeUnit.SECONDS);
            second.get(8, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(harness.enabled("operator")).isFalse();
        assertThat(harness.managementAudits("operator")).isEqualTo(raceBefore + 1);
        assertThat(harness.applicationStatus("APP-RACE")).isEqualTo("EXECUTED");
    }

    private void assertServiceOverlap(ServiceHarness harness) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger readyReads = new AtomicInteger();
        AuthAccountPreApprovalApplicationRepository gated = new AuthAccountPreApprovalApplicationRepository() {
            @Override
            public AuthAccountPreApprovalApplication save(AuthAccountPreApprovalApplication application) {
                return harness.applications().save(application);
            }

            @Override
            public Optional<AuthAccountPreApprovalApplication> findByCode(String applicationCode) {
                Optional<AuthAccountPreApprovalApplication> found = harness.applications().findByCode(applicationCode);
                if (found.isPresent() && found.get().executable() && readyReads.incrementAndGet() <= 2) {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                return found;
            }

            @Override
            public List<AuthAccountPreApprovalApplication> findAll() {
                return harness.applications().findAll();
            }
        };
        harness.applications().save(disableApplication("APP-OVERLAP"));
        AuthUserAccountManagementService service = harness.service(gated);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> runService(service, success, rejected));
            Future<?> second = pool.submit(() -> runService(service, success, rejected));
            first.get(8, TimeUnit.SECONDS);
            second.get(8, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(harness.accounts().findByUsername("operator").orElseThrow().enabled()).isFalse();
        assertThat(harness.applications().findByCode("APP-OVERLAP").orElseThrow().applicationStatus()).isEqualTo("EXECUTED");
    }

    private void runCommit(Harness harness, String applicationCode, String auditId, AtomicInteger success, AtomicInteger rejected) {
        try {
            harness.actions().commit(disableMutation("operator", applicationCode, auditId));
            success.incrementAndGet();
        } catch (BusinessException ex) {
            rejected.incrementAndGet();
        }
    }

    private void runService(AuthUserAccountManagementService service, AtomicInteger success, AtomicInteger rejected) {
        try {
            service.updateEnabled("operator", false, "重叠停用", "APP-OVERLAP", admin());
            success.incrementAndGet();
        } catch (BusinessException ex) {
            rejected.incrementAndGet();
        }
    }

    private Harness memoryHarness() {
        InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        InMemoryAuthAuditRepository audits = new InMemoryAuthAuditRepository();
        InMemoryAuthAccountPreApprovalApplicationRepository applications =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        AtomicReference<String> failure = new AtomicReference<>("none");
        AuthAccountActionProbe probe = probe(failure);
        InMemoryAuthAccountActionRepository actions =
                new InMemoryAuthAccountActionRepository(accounts, audits, applications, probe);
        return new Harness() {
            @Override
            public AuthAccountActionRepository actions() {
                return actions;
            }

            @Override
            public void reset(String username) {
                accounts.save(enabledAccount(username));
                failure.set("none");
            }

            @Override
            public void saveApplication(AuthAccountPreApprovalApplication application) {
                applications.save(application);
            }

            @Override
            public void failAt(String point) {
                failure.set(point);
            }

            @Override
            public boolean enabled(String username) {
                return accounts.findByUsername(username).orElseThrow().enabled();
            }

            @Override
            public String applicationStatus(String code) {
                return applications.findByCode(code).orElseThrow().applicationStatus();
            }

            @Override
            public long managementAudits(String username) {
                return audits.findRecent(20).stream()
                        .filter(event -> "ACCOUNT_MANAGEMENT".equals(event.eventType()) && username.equals(event.username()))
                        .count();
            }
        };
    }

    private Harness jdbcHarness() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:account-action-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        JdbcAuthUserAccountRepository accounts = new JdbcAuthUserAccountRepository(dataSource);
        JdbcAuthAuditRepository audits = new JdbcAuthAuditRepository(dataSource);
        JdbcAuthAccountPreApprovalApplicationRepository applications =
                new JdbcAuthAccountPreApprovalApplicationRepository(dataSource);
        AtomicReference<String> failure = new AtomicReference<>("none");
        JdbcAuthAccountActionRepository actions = new JdbcAuthAccountActionRepository(
                dataSource, accounts, audits, applications, probe(failure));
        return new Harness() {
            @Override
            public AuthAccountActionRepository actions() {
                return actions;
            }

            @Override
            public void reset(String username) {
                accounts.save(enabledAccount(username));
                failure.set("none");
            }

            @Override
            public void saveApplication(AuthAccountPreApprovalApplication application) {
                applications.save(application);
            }

            @Override
            public void failAt(String point) {
                failure.set(point);
            }

            @Override
            public boolean enabled(String username) {
                return accounts.findByUsername(username).orElseThrow().enabled();
            }

            @Override
            public String applicationStatus(String code) {
                return applications.findByCode(code).orElseThrow().applicationStatus();
            }

            @Override
            public long managementAudits(String username) {
                return audits.findRecent(20).stream()
                        .filter(event -> "ACCOUNT_MANAGEMENT".equals(event.eventType()) && username.equals(event.username()))
                        .count();
            }
        };
    }

    private ServiceHarness memoryServiceHarness() {
        InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        InMemoryAuthAuditRepository audits = new InMemoryAuthAuditRepository();
        InMemoryAuthAccountPreApprovalApplicationRepository applications =
                new InMemoryAuthAccountPreApprovalApplicationRepository();
        AuthAccountActionRepository actions = new InMemoryAuthAccountActionRepository(accounts, audits, applications);
        return new ServiceHarness(accounts, applications, actions);
    }

    private ServiceHarness jdbcServiceHarness() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:account-service-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        JdbcAuthUserAccountRepository accounts = new JdbcAuthUserAccountRepository(dataSource);
        JdbcAuthAuditRepository audits = new JdbcAuthAuditRepository(dataSource);
        JdbcAuthAccountPreApprovalApplicationRepository applications =
                new JdbcAuthAccountPreApprovalApplicationRepository(dataSource);
        accounts.save(enabledAccount("operator"));
        AuthAccountActionRepository actions = new JdbcAuthAccountActionRepository(
                dataSource, accounts, audits, applications);
        return new ServiceHarness(accounts, applications, actions);
    }

    private AuthAccountActionProbe probe(AtomicReference<String> failure) {
        return new AuthAccountActionProbe() {
            @Override
            public void beforeAccountWrite() {
                failIf(failure, "account");
            }

            @Override
            public void beforeAuditWrite() {
                failIf(failure, "audit");
            }

            @Override
            public void beforeApplicationWrite() {
                failIf(failure, "application");
            }
        };
    }

    private void failIf(AtomicReference<String> failure, String point) {
        if (point.equals(failure.get())) {
            throw new IllegalStateException(point + " save failed");
        }
    }

    private AuthAccountMutation disableMutation(String username, String applicationCode, String auditId) {
        return AuthAccountMutation.setEnabled(username, false, audit(username, auditId), disableApplication(applicationCode));
    }

    private AuthAuditEvent audit(String username, String auditId) {
        return new AuthAuditEvent(
                auditId,
                "ACCOUNT_MANAGEMENT",
                username,
                "系统管理员",
                "ADMIN",
                "SUCCESS",
                "停用账号",
                "2026-10-10 23:00:00",
                "abcdef"
        );
    }

    private AuthUserAccount enabledAccount(String username) {
        return new AuthUserAccount(
                username,
                "hash",
                "操作工",
                RoleCode.OPERATOR,
                DataScope.SELF,
                true,
                false
        );
    }

    private AuthAccountPreApprovalApplication disableApplication(String code) {
        return disableApplication(code, "operator");
    }

    private AuthAccountPreApprovalApplication disableApplication(String code, String username) {
        return new AuthAccountPreApprovalApplication(
                code,
                "DRAFT-" + code,
                "AUTH-PRE-STATUS-001",
                "账号启停/解锁",
                username,
                "系统管理员",
                "ADMIN",
                "操作工",
                "OPERATOR",
                "SELF",
                List.of(),
                false,
                false,
                "岗位交接停用",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("ADMIN"),
                "可进入阻断式审批",
                "READY_FOR_BLOCKING_APPROVAL",
                true,
                "contract",
                "允许执行",
                "中",
                "green",
                "",
                "2026-10-10 10:00:00",
                List.of(),
                "DISABLE"
        );
    }

    private OperatorContext admin() {
        return new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "admin-token-abcdef");
    }

    private void createSchema(JdbcDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table auth_user_account (
                        id bigint primary key auto_increment,
                        username varchar(64) not null,
                        password_hash varchar(255) not null,
                        operator varchar(128) not null,
                        role_code varchar(64) not null,
                        data_scope varchar(64) not null,
                        customer_scope varchar(512) not null default '',
                        enabled boolean not null default true,
                        demo_account boolean not null default false,
                        password_changed_at timestamp default current_timestamp,
                        password_change_required boolean not null default false,
                        temporary_password_consumed boolean not null default false,
                        allowed_processes varchar(64) not null default '',
                        failed_login_count int not null default 0,
                        locked_until timestamp null,
                        last_login_at timestamp null,
                        last_failed_login_at timestamp null,
                        unique key uk_auth_user_account_username (username)
                    )
                    """);
            statement.execute("""
                    create table auth_audit_event (
                        id bigint primary key auto_increment,
                        event_id varchar(64) not null,
                        event_type varchar(32) not null,
                        username varchar(64),
                        operator varchar(128),
                        role varchar(64),
                        result varchar(32) not null,
                        reason varchar(255) not null,
                        occurred_at varchar(64) not null,
                        session_token_suffix varchar(16),
                        previous_event_hash varchar(64) not null default '',
                        event_hash varchar(64) not null default '',
                        unique key uk_auth_audit_event_id (event_id)
                    )
                    """);
            statement.execute("""
                    create table auth_account_pre_approval_application (
                        id bigint primary key auto_increment,
                        application_code varchar(64) not null,
                        draft_code varchar(128) not null,
                        readiness_code varchar(64) not null,
                        action_type varchar(64) not null,
                        target_username varchar(64) not null,
                        applicant varchar(128) not null,
                        applicant_role varchar(64) not null,
                        target_operator varchar(128) not null default '',
                        target_role varchar(64) not null default '',
                        target_data_scope varchar(64) not null default '',
                        target_customer_scope varchar(512) not null default '',
                        target_enabled boolean null,
                        demo_account boolean null,
                        reason varchar(512) not null default '',
                        required_evidence text not null,
                        provided_evidence text not null,
                        missing_evidence text not null,
                        missing_capabilities text not null,
                        approver_roles varchar(512) not null default '',
                        blocking_status varchar(64) not null,
                        application_status varchar(64) not null,
                        approval_task_code varchar(64) not null default '',
                        ready_for_blocking_approval boolean not null default false,
                        source_summary text not null,
                        next_action varchar(512) not null default '',
                        risk_level varchar(32) not null default '',
                        tone varchar(32) not null default '',
                        submitted_at varchar(32) not null,
                        target_allowed_processes text null,
                        status_action varchar(16) not null default '',
                        unique key uk_auth_pre_approval_application_code (application_code)
                    )
                    """);
        }
    }

    private interface Harness {
        AuthAccountActionRepository actions();

        void reset(String username);

        void saveApplication(AuthAccountPreApprovalApplication application);

        void failAt(String point);

        boolean enabled(String username);

        String applicationStatus(String code);

        long managementAudits(String username);
    }

    private final class ServiceHarness {
        private final AuthUserAccountRepository accounts;
        private final AuthAccountPreApprovalApplicationRepository applications;
        private final AuthAccountActionRepository actions;
        private final PasswordHasher passwordHasher = new PasswordHasher();

        private ServiceHarness(
                AuthUserAccountRepository accounts,
                AuthAccountPreApprovalApplicationRepository applications,
                AuthAccountActionRepository actions
        ) {
            this.accounts = accounts;
            this.applications = applications;
            this.actions = actions;
        }

        private AuthUserAccountRepository accounts() {
            return accounts;
        }

        private AuthAccountPreApprovalApplicationRepository applications() {
            return applications;
        }

        private AuthUserAccountManagementService service(AuthAccountPreApprovalApplicationRepository gated) {
            return new AuthUserAccountManagementService(
                    accounts,
                    passwordHasher,
                    new PasswordPolicy(),
                    new PasswordExpiryPolicy(),
                    new InMemoryAuthAuditRepository(),
                    null,
                    gated,
                    null,
                    null,
                    null,
                    false,
                    null,
                    actions
            );
        }
    }

    private static final class FlakySessions extends AuthSessionService {
        private boolean fail;

        private FlakySessions(AuthUserAccountRepository accounts, AuthAuditRepository audits) {
            super(
                    new NavigationMenuService(new InMemoryNavigationMenuRepository()),
                    audits,
                    accounts,
                    new PasswordHasher(),
                    new PasswordExpiryPolicy(),
                    new PasswordPolicy()
            );
        }

        @Override
        public int revokeSessionsForUsername(String username, String reason) {
            if (fail) {
                throw new IllegalStateException("session store down");
            }
            return super.revokeSessionsForUsername(username, reason);
        }
    }
}
