package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.infra.InMemoryAuthAccountActionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.JdbcAuthAccountActionRepository;
import com.zhubao.erp.security.infra.JdbcAuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.infra.JdbcAuthAuditRepository;
import com.zhubao.erp.security.infra.JdbcAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerAuthorizationRepository;
import com.zhubao.erp.security.infra.JdbcCustomerAuthorizationRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

/** 内存与 H2 验证账号、客户授权、审计原子性以及最后管理员并发保护。 */
class AuthAccountActionContractTest {

    @Test
    void 缺省账号保存和删除在内存及Jdbc中原子提交授权和审计() throws Exception {
        for (boolean jdbc : List.of(false, true)) {
            DirectHarness harness = directHarness(jdbc);
            harness.accounts().save(enabledAccount("target"));
            CustomerAuthorization original = new CustomerAuthorization("target", "CUST-OLD", "原客户", "管理员", "2026-10-13T10:00:00");
            harness.customers().grant(original);
            AuthUserAccount proposed = new AuthUserAccount("target", "new-hash", "新操作人", RoleCode.SALES,
                    DataScope.CUSTOMER_RANGE, true, false);
            harness.failure().set("audit");
            assertThatThrownBy(() -> harness.actions().commit(AuthAccountMutation.save(proposed, audit("target", "save-rollback"))
                    .withCustomerAuthorizations(List.of(new CustomerAuthorization("target", "CUST-NEW", "新客户", "管理员", "2026-10-13T11:00:00")))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(harness.accounts().findByUsername("target").orElseThrow().passwordHash()).isEqualTo("hash");
            assertThat(harness.customers().findByUsername("target")).containsExactly(original);
            assertThat(harness.audits().findRecent(20)).isEmpty();
            assertThatThrownBy(() -> harness.actions().commit(AuthAccountMutation.delete("target", audit("target", "delete-rollback"))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(harness.accounts().findByUsername("target")).isPresent();
            assertThat(harness.customers().findByUsername("target")).containsExactly(original);
            assertThat(harness.audits().findRecent(20)).isEmpty();
            harness.failure().set("none");
            harness.actions().commit(AuthAccountMutation.delete("target", audit("target", "delete-success")));
            assertThat(harness.accounts().findByUsername("target")).isEmpty();
            assertThat(harness.customers().findByUsername("target")).isEmpty();
            assertThat(harness.audits().findRecent(20)).singleElement()
                    .satisfies(event -> assertThat(event.eventId()).isEqualTo("delete-success"));
        }
    }

    @Test
    void 内存和Jdbc并发移除管理员仍保留至少一人() throws Exception {
        for (boolean jdbc : List.of(false, true)) {
            DirectHarness harness = directHarness(jdbc);
            harness.accounts().save(new AuthUserAccount("admin", "hash", "管理员一", RoleCode.ADMIN, DataScope.ALL, true, false));
            harness.accounts().save(new AuthUserAccount("admin2", "hash", "管理员二", RoleCode.ADMIN, DataScope.ALL, true, false));
            AtomicInteger success = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            CyclicBarrier ready = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = pool.submit(() -> removeAdmin(harness, "admin", ready, success, rejected));
                Future<?> second = pool.submit(() -> removeAdmin(harness, "admin2", ready, success, rejected));
                first.get(8, TimeUnit.SECONDS);
                second.get(8, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            assertThat(success.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(1);
            assertThat(harness.accounts().findAll().stream().filter(account -> account.enabled() && account.role() == RoleCode.ADMIN)).hasSize(1);
        }
    }

    private void removeAdmin(DirectHarness harness, String username, CyclicBarrier ready,
                             AtomicInteger success, AtomicInteger rejected) {
        try {
            ready.await(5, TimeUnit.SECONDS);
            harness.actions().commit(AuthAccountMutation.delete(username, audit(username, "remove-" + username)));
            success.incrementAndGet();
        } catch (BusinessException exception) {
            rejected.incrementAndGet();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DirectHarness directHarness(boolean jdbc) throws SQLException {
        AtomicReference<String> failure = new AtomicReference<>("none");
        if (!jdbc) {
            var accounts = com.zhubao.erp.security.infra.AuthAccountFixture.repository(new PasswordHasher());
            var audits = new InMemoryAuthAuditRepository();
            var customers = new InMemoryCustomerAuthorizationRepository();
            var applications = new InMemoryAuthAccountPreApprovalApplicationRepository();
            return new DirectHarness(accounts, audits, customers,
                    new InMemoryAuthAccountActionRepository(accounts, audits, customers, probe(failure)), failure);
        }
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:account-direct-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        createSchema(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table sys_customer_authorization (
                        username varchar(64) not null, customer_code varchar(64) not null,
                        customer_name varchar(128) not null, granted_by varchar(128) not null,
                        granted_at varchar(64) not null, primary key (username, customer_code)
                    )
                    """);
        }
        var accounts = new JdbcAuthUserAccountRepository(dataSource);
        var audits = new JdbcAuthAuditRepository(dataSource);
        var customers = new JdbcCustomerAuthorizationRepository(dataSource);
        var applications = new JdbcAuthAccountPreApprovalApplicationRepository(dataSource);
        return new DirectHarness(accounts, audits, customers,
                new JdbcAuthAccountActionRepository(dataSource, accounts, audits, customers, probe(failure)), failure);
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

        };
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
                        cover_duties varchar(512) not null default '',
                        team_code varchar(64) not null default '',
                        department_code varchar(64) not null default '',
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
    private static void failIf(AtomicReference<String> failure, String point) {
        if (point.equals(failure.get())) throw new IllegalStateException("模拟" + point + "失败");
    }

    @Test
    void 停用和解锁与审计同次回滚() throws Exception {
        for (boolean jdbc : List.of(false, true)) {
            var harness = directHarness(jdbc);
            harness.accounts().save(enabledAccount("target"));
            harness.accounts().recordLoginFailure("target", 5, java.time.Instant.now().plusSeconds(600));
            var before = harness.accounts().findByUsername("target").orElseThrow();
            harness.failure().set("audit");
            assertThatThrownBy(() -> harness.actions().commit(AuthAccountMutation.setEnabled("target", false, audit("target", "status-fail"))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(harness.accounts().findByUsername("target").orElseThrow()).isEqualTo(before);
            assertThatThrownBy(() -> harness.actions().commit(AuthAccountMutation.unlock("target", audit("target", "unlock-fail"))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(harness.accounts().findByUsername("target").orElseThrow()).isEqualTo(before);
            assertThat(harness.audits().findRecent(20)).isEmpty();
            harness.failure().set("none");
            harness.actions().commit(AuthAccountMutation.unlock("target", audit("target", "unlock-ok")));
            assertThat(harness.accounts().findByUsername("target").orElseThrow().lockedUntil()).isNull();
        }
    }

    private record DirectHarness(AuthUserAccountRepository accounts, AuthAuditRepository audits,
                                 CustomerAuthorizationRepository customers, AuthAccountActionRepository actions,
                                 AtomicReference<String> failure) { }
}
