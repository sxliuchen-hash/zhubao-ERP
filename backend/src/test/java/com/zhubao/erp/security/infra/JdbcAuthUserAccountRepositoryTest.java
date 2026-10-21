package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAuthUserAccountRepositoryTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private JdbcAuthUserAccountRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        repository = new JdbcAuthUserAccountRepository(dataSource);
    }

    @Test
    void savesReadsAndUpdatesUserAccount() {
        repository.save(account("planner", "计划员 陈敏", RoleCode.SALES, DataScope.CUSTOMER_RANGE, true));

        assertThat(repository.findByUsername("planner"))
                .get()
                .satisfies(account -> {
                    assertThat(account.username()).isEqualTo("planner");
                    assertThat(account.operator()).isEqualTo("计划员 陈敏");
                    assertThat(account.role()).isEqualTo(RoleCode.SALES);
                    assertThat(account.dataScope()).isEqualTo(DataScope.CUSTOMER_RANGE);
                    assertThat(account.customerScope()).containsExactly("恒信珠宝", "雅辰饰品");
                    assertThat(account.enabled()).isTrue();
                    assertThat(account.demoAccount()).isFalse();
                    assertThat(account.passwordChangedAt()).isNotNull();
                    assertThat(account.passwordChangeRequired()).isFalse();
                    assertThat(passwordHasher.matches("planner-pass", account.passwordHash())).isTrue();
                });

        repository.save(account("planner", "计划员 陈敏-停用", RoleCode.OPERATOR, DataScope.SELF, false));

        assertThat(repository.findByUsername("planner"))
                .get()
                .satisfies(account -> {
                    assertThat(account.operator()).isEqualTo("计划员 陈敏-停用");
                    assertThat(account.role()).isEqualTo(RoleCode.OPERATOR);
                    assertThat(account.enabled()).isFalse();
                });
    }

    @Test
    void organizationSaveRequiresColumnsAndPersistsAfterMigration() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        createSchema(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("alter table auth_user_account drop column team_code");
            statement.execute("alter table auth_user_account drop column department_code");
        }
        JdbcAuthUserAccountRepository legacy = new JdbcAuthUserAccountRepository(dataSource);
        AuthUserAccount leader = account("leader-org", "组长", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, true);
        assertThatThrownBy(() -> legacy.save(leader.withOrganization("TEAM-A", "")))
                .hasMessageContaining("数据库尚未升级组织字段");
        assertThat(legacy.findByUsername("leader-org")).isEmpty();

        legacy.save(leader);
        assertThatThrownBy(() -> legacy.save(leader.withOrganization("TEAM-A", "")))
                .hasMessageContaining("数据库尚未升级组织字段");
        assertThat(legacy.findByUsername("leader-org")).get().extracting(AuthUserAccount::teamCode).isEqualTo("");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("alter table auth_user_account add column team_code varchar(40) not null default ''");
            statement.execute("alter table auth_user_account add column department_code varchar(40) not null default ''");
        }
        JdbcAuthUserAccountRepository upgraded = new JdbcAuthUserAccountRepository(dataSource);
        upgraded.save(leader.withOrganization("TEAM-A", "DEPT-A"));
        assertThat(upgraded.findByUsername("leader-org")).get().satisfies(saved -> {
            assertThat(saved.teamCode()).isEqualTo("TEAM-A");
            assertThat(saved.departmentCode()).isEqualTo("DEPT-A");
        });
    }

    @Test
    void persistsPasswordChangeRequiredFlag() {
        repository.save(new AuthUserAccount(
                "temp",
                passwordHasher.hash("temp-pass"),
                "临时密码账号",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                Instant.parse("2026-07-09T00:00:00Z"),
                0,
                null,
                List.of("恒信珠宝"),
                true
        ));

        assertThat(repository.findByUsername("temp"))
                .get()
                .satisfies(account -> {
                    assertThat(account.passwordChangeRequired()).isTrue();
                    assertThat(account.temporaryPasswordConsumed()).isFalse();
                    assertThat(account.customerScope()).containsExactly("恒信珠宝");
                });

        assertThat(repository.consumeTemporaryPassword("temp")).isTrue();
        assertThat(repository.consumeTemporaryPassword("temp")).isFalse();
        assertThat(repository.findByUsername("temp"))
                .get()
                .satisfies(account -> {
                    assertThat(account.passwordChangeRequired()).isTrue();
                    assertThat(account.temporaryPasswordConsumed()).isTrue();
                });
    }

    @Test
    void listsOnlyEnabledUsernames() {
        repository.save(account("warehouse", "库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, true));
        repository.save(account("disabled", "停用账号", RoleCode.OPERATOR, DataScope.SELF, false));
        repository.save(account("admin", "管理员", RoleCode.ADMIN, DataScope.ALL, true));

        assertThat(repository.findActiveUsernames()).containsExactly("admin", "warehouse");
    }

    @Test
    void supportsManagementListStatusUpdateAndUnlock() {
        repository.save(account("warehouse", "库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, true));
        Instant passwordChangedAt = Instant.parse("2026-01-01T00:00:00Z");
        repository.save(new AuthUserAccount(
                "leader",
                passwordHasher.hash("leader-pass"),
                "车间组长",
                RoleCode.WORKSHOP_LEADER,
                DataScope.TEAM,
                true,
                false,
                passwordChangedAt,
                5,
                Instant.now().plusSeconds(900)
        ));

        assertThat(repository.findAll())
                .extracting(AuthUserAccount::username)
                .containsExactly("leader", "warehouse");

        repository.updateEnabled("leader", false);

        assertThat(repository.findByUsername("leader"))
                .get()
                .extracting(AuthUserAccount::enabled)
                .isEqualTo(false);

        repository.unlock("leader");

        assertThat(repository.findByUsername("leader"))
                .get()
                .satisfies(account -> {
                    assertThat(account.failedLoginCount()).isZero();
                    assertThat(account.lockedUntil()).isNull();
                    assertThat(account.passwordChangedAt()).isEqualTo(passwordChangedAt);
                });
    }

    @Test
    void recordsLoginFailureLockoutAndClearsOnSuccess() {
        repository.save(account("leader", "车间组长", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, true));
        Instant lockedUntil = Instant.now().plusSeconds(900);

        repository.recordLoginFailure("leader", 5, lockedUntil);

        assertThat(repository.findByUsername("leader"))
                .get()
                .satisfies(account -> {
                    assertThat(account.failedLoginCount()).isEqualTo(5);
                    assertThat(account.lockedUntil()).isNotNull();
                });

        repository.recordLoginSuccess("leader");

        assertThat(repository.findByUsername("leader"))
                .get()
                .satisfies(account -> {
                    assertThat(account.failedLoginCount()).isZero();
                    assertThat(account.lockedUntil()).isNull();
                });
    }

    private AuthUserAccount account(
            String username,
            String operator,
            RoleCode role,
            DataScope dataScope,
            boolean enabled
    ) {
        return new AuthUserAccount(
                username,
                passwordHasher.hash(username + "-pass"),
                operator,
                role,
                dataScope,
                enabled,
                false,
                dataScope == DataScope.CUSTOMER_RANGE ? List.of("恒信珠宝", "雅辰饰品") : List.of()
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
                        cover_duties varchar(160) not null default '',
                        team_code varchar(40) not null default '',
                        department_code varchar(40) not null default '',
                        failed_login_count int not null default 0,
                        locked_until timestamp null,
                        last_login_at timestamp null,
                        last_failed_login_at timestamp null,
                        unique key uk_auth_user_account_username (username)
                    )
                    """);
        }
    }
}
