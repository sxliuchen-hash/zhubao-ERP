package com.zhubao.erp.security.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MasterDataRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.domain.AuthAccountSnapshot;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthLoginSession;
import com.zhubao.erp.security.domain.AuthTokenDigest;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CoverDuties;
import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.CustomerPortalIdentityAction;
import com.zhubao.erp.security.domain.CustomerPortalRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.error.SessionInvalidException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthLoginSessionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalBindingRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import com.zhubao.erp.security.infra.JdbcAuthAuditRepository;
import com.zhubao.erp.security.infra.JdbcAuthUserAccountRepository;
import com.zhubao.erp.security.infra.JdbcCustomerPortalBindingRepository;
import com.zhubao.erp.security.infra.JdbcCustomerPortalIdentityActionRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 客户入口身份只绑定一个稳定客户，不借用内部权限，也不签发未核定凭据。 */
class CustomerPortalIdentityTest {

    @Test
    void 绑定稳定客户且不能借用内部权限() {
        Fixture fixture = memory();
        CustomerPortalBinding binding = fixture.service().bind(fixture.admin(), "Portal-A", fixture.customerId());

        assertThat(binding.customerId()).isEqualTo(fixture.customerId());
        assertThat(binding.username()).isEqualTo("portal-a");
        AuthUserAccount account = fixture.accounts().findByUsername("portal-a").orElseThrow();
        assertThat(account.role()).isEqualTo(RoleCode.CUSTOMER_PORTAL);
        assertThat(account.dataScope()).isEqualTo(DataScope.CUSTOMER_RANGE);
        assertThat(account.customerScope()).containsExactly(Long.toString(fixture.customerId()));
        assertThat(account.coverDuties()).isEmpty();
        assertThat(account.passwordHash()).isEqualTo(CustomerPortalRules.UNISSUED_PASSWORD);

        OperatorContext portal = fixture.service().portalOperator("portal-a");
        assertThat(portal.customerScope()).containsExactly(Long.toString(fixture.customerId()));
        assertThat(portal.coverDuties()).isEmpty();
        assertThatThrownBy(() -> fixture.guard().assertAllowed(PermissionCode.FINANCE_PAYROLL, portal))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.guard().assertAllowed(PermissionCode.STOCK_COMPANY_ISSUE, portal))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> fixture.guard().assertAllowed(PermissionCode.BUSINESS_OUTBOUND_SHIPMENT, portal))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(fixture.guard().effectivePermissions(RoleCode.CUSTOMER_PORTAL, List.of(CoverDuties.WAREHOUSE))).isEmpty();
        assertThat(fixture.guard().allowedPermissionsFor(RoleCode.FINANCE_STATISTICIAN)).contains(PermissionCode.FINANCE_PAYROLL);
        assertThat(fixture.guard().allowedPermissionsFor(RoleCode.WAREHOUSE_KEEPER)).contains(PermissionCode.STOCK_COMPANY_ISSUE);

        assertThat(fixture.service().bind(fixture.admin(), "portal-a", fixture.customerId()).customerId())
                .isEqualTo(fixture.customerId());
        assertThat(fixture.accounts().findByUsername("portal-a").orElseThrow().passwordHash())
                .isEqualTo(CustomerPortalRules.UNISSUED_PASSWORD);
    }

    @Test
    void 空绑定和自填编号拒绝且不认领其他客户() {
        Fixture fixture = memory();
        long otherId = fixture.customers().saveCustomer(
                new CustomerProfile(0, "YC", "雅辰饰品", "", "", "", "", true)).customerId();
        long disabledId = fixture.customers().saveCustomer(
                new CustomerProfile(8, "STOP", "停用客户", "", "", "", "", false)).customerId();

        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-b", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("客户绑定不能为空");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-b", 0L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("客户绑定不能为空");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), " ", fixture.customerId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("客户账号不能为空");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-b", 999L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能按名称或自填编号认领");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-b", disabledId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("客户主体已停用");
        assertThatThrownBy(() -> fixture.service().refusePresentedClaim(fixture.admin(), "portal-b", "恒信珠宝"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能按客户名称或自填编号自动认领");
        assertThatThrownBy(() -> fixture.service().refusePresentedClaim(fixture.admin(), "portal-b", "HX"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能按客户名称或自填编号自动认领");
        assertThatThrownBy(() -> fixture.service().refusePresentedClaim(
                fixture.admin(), "portal-b", Long.toString(fixture.customerId())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能按客户名称或自填编号自动认领");

        assertThat(fixture.accounts().findByUsername("portal-b")).isEmpty();
        assertThat(fixture.bindings().findByCustomerId(otherId)).isEmpty();
        assertThatThrownBy(() -> fixture.service().bind(
                new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL),
                "portal-b", fixture.customerId())).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 不能借用内部账号或把一个客户绑给多人() {
        Fixture fixture = memory();
        fixture.accounts().save(internal("finance", RoleCode.FINANCE_STATISTICIAN));
        fixture.accounts().save(internal("warehouse", RoleCode.WAREHOUSE_KEEPER));
        fixture.accounts().save(internal("biz", RoleCode.BUSINESS_MANAGER));
        fixture.service().bind(fixture.admin(), "portal-a", fixture.customerId());
        long otherId = fixture.customers().saveCustomer(
                new CustomerProfile(0, "YC", "雅辰饰品", "", "", "", "", true)).customerId();

        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "finance", fixture.customerId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能借用内部账号");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "warehouse", otherId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能借用内部账号");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "biz", otherId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能借用内部账号");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-a", otherId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("一个客户账号只能绑定一个客户主体");
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-c", fixture.customerId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("本版一个客户只绑定一个账号");
        assertThat(fixture.accounts().findByUsername("finance").orElseThrow().role()).isEqualTo(RoleCode.FINANCE_STATISTICIAN);
        assertThat(fixture.bindings().findByCustomerId(otherId)).isEmpty();

        var management = new AuthUserAccountManagementService(
                fixture.accounts(), new PasswordHasher(), new PasswordPolicy(), new PasswordExpiryPolicy(),
                new InMemoryAuthAuditRepository());
        assertThatThrownBy(() -> management.upsert(new ManageAuthUserAccountCommand(
                "portal-x", "Secret123!", "某人", "CUSTOMER_PORTAL", "ALL",
                List.of("1"), true, false, null, List.of(), List.of(CoverDuties.WAREHOUSE)), fixture.admin()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能通过内部账号管理办理");
        assertThat(fixture.accounts().findByUsername("portal-x")).isEmpty();
        assertThatThrownBy(() -> management.upsert(new ManageAuthUserAccountCommand(
                "portal-a", "Secret123!", "某人", "WAREHOUSE_KEEPER", "ALL",
                List.of(), true, false), fixture.admin()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能通过内部账号管理办理");
        assertThat(fixture.accounts().findByUsername("portal-a").orElseThrow().role()).isEqualTo(RoleCode.CUSTOMER_PORTAL);
        assertThatThrownBy(() -> management.updateEnabled("portal-a", false, "内部停用", fixture.admin()))
                .isInstanceOf(BusinessException.class);
        assertThat(fixture.accounts().findByUsername("portal-a").orElseThrow().enabled()).isTrue();
    }

    @Test
    void 凭据未核定不能登录且停用后会话失效() {
        Fixture fixture = memory();
        fixture.service().bind(fixture.admin(), "portal-a", fixture.customerId());
        assertThatThrownBy(() -> fixture.sessions().login(new AuthLoginCommand("portal-a", "Secret123!")))
                .isInstanceOf(PermissionDeniedException.class).hasMessage("客户账户还没有口令，不能登录");
        fixture.service().issueCredential(fixture.admin(), "portal-a", "Secret123!");
        assertThat(fixture.sessions().login(new AuthLoginCommand("portal-a", "Secret123!")).token()).isNotBlank();
        fixture.service().recoverCredential(fixture.admin(), "portal-a", "Reset123!");
        assertThatThrownBy(() -> fixture.sessions().login(new AuthLoginCommand("portal-a", "Secret123!")))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(fixture.sessions().login(new AuthLoginCommand("portal-a", "Reset123!")).token()).isNotBlank();

        AuthUserAccount account = fixture.accounts().findByUsername("portal-a").orElseThrow();
        String token = "portal-session-token";
        fixture.loginSessions().save(new AuthLoginSession(
                AuthTokenDigest.sha256(token),
                account.username(),
                account.operator(),
                account.role().name(),
                Instant.now().plusSeconds(3600),
                AuthAccountSnapshot.fingerprint(account),
                "token"
        ));
        var session = fixture.sessions().session("Bearer " + token).orElseThrow();
        assertThat(session.role()).isEqualTo("CUSTOMER_PORTAL");
        assertThat(session.roleLabel()).isEqualTo("客户只读");
        assertThat(session.dataScope()).isEqualTo("CUSTOMER_RANGE");
        assertThat(session.customerScope()).containsExactly(Long.toString(fixture.customerId()));
        assertThat(session.menuCodes()).isEmpty();
        assertThat(session.coverDuties()).isEmpty();
        assertThat(session.effectivePermissions()).isEmpty();

        assertThat(fixture.service().disable(fixture.admin(), "portal-a")).isEqualTo(2);
        assertThat(fixture.accounts().findByUsername("portal-a").orElseThrow().enabled()).isFalse();
        assertThat(fixture.bindings().findByUsername("portal-a").orElseThrow().enabled()).isFalse();
        assertThatThrownBy(() -> fixture.sessions().session("Bearer " + token)).isInstanceOf(SessionInvalidException.class);
        assertThat(fixture.service().disable(fixture.admin(), "portal-a")).isZero();
        assertThatThrownBy(() -> fixture.service().bind(fixture.admin(), "portal-a", fixture.customerId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("客户身份已停用");
        assertThatThrownBy(() -> fixture.sessions().login(new AuthLoginCommand("portal-a", "Secret123!")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 全部数据权限或顶岗不能继续作为客户身份() {
        Fixture fixture = memory();
        fixture.service().bind(fixture.admin(), "portal-a", fixture.customerId());
        AuthUserAccount current = fixture.accounts().findByUsername("portal-a").orElseThrow();
        fixture.accounts().save(new AuthUserAccount(
                current.username(), current.passwordHash(), current.operator(), current.role(),
                DataScope.ALL, true, false, current.passwordChangedAt(), 0, null,
                current.customerScope(), false, false, List.of(), List.of(CoverDuties.WAREHOUSE)));
        assertThatThrownBy(() -> fixture.service().portalOperator("portal-a"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("不能携带内部全部数据权限或顶岗");
        assertThatThrownBy(() -> fixture.service().portalOperator("admin"))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("不是客户身份");
    }

    @Test
    void 审计失败不留下账号和绑定() {
        var accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        var bindings = new InMemoryCustomerPortalBindingRepository();
        var customers = new InMemoryMasterDataRepository();
        long customerId = customers.saveCustomer(
                new CustomerProfile(0, "HX", "恒信珠宝", "", "", "", "", true)).customerId();
        AuthAuditRepository audits = new AuthAuditRepository() {
            @Override
            public AuthAuditEvent save(AuthAuditEvent event) {
                throw new IllegalStateException("审计失败");
            }

            @Override
            public List<AuthAuditEvent> findRecent(int limit) {
                return List.of();
            }
        };
        var sessions = sessionsFor(accounts, bindings);
        var service = new CustomerPortalIdentityService(accounts, bindings, customers,
                new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, audits), sessions);
        assertThatThrownBy(() -> service.bind(admin(), "portal-a", customerId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("审计失败");
        assertThat(accounts.findByUsername("portal-a")).isEmpty();
        assertThat(bindings.findByUsername("portal-a")).isEmpty();
    }

    @Test
    void jdbc绑定停用提交且重复客户回滚() throws Exception {
        assertThat(MigrationCatalog.loadDirectory(Path.of("src/main/resources/db/mysql")))
                .extracting(MigrationScript::version)
                .contains(106);
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:portal-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);
        var accounts = new JdbcAuthUserAccountRepository(dataSource);
        var bindings = new JdbcCustomerPortalBindingRepository(dataSource);
        var actions = new JdbcCustomerPortalIdentityActionRepository(
                dataSource, accounts, bindings, new JdbcAuthAuditRepository(dataSource));
        var customers = new InMemoryMasterDataRepository();
        long customerId = customers.saveCustomer(
                new CustomerProfile(0, "HX", "恒信珠宝", "", "", "", "", true)).customerId();
        long otherId = customers.saveCustomer(
                new CustomerProfile(0, "YC", "雅辰饰品", "", "", "", "", true)).customerId();
        var loginSessions = new InMemoryAuthLoginSessionRepository();
        var sessions = sessionsFor(accounts, bindings, loginSessions);
        var service = new CustomerPortalIdentityService(accounts, bindings, customers, actions, sessions);

        service.bind(admin(), "portal-a", customerId);
        assertThat(accounts.findByUsername("portal-a").orElseThrow().role()).isEqualTo(RoleCode.CUSTOMER_PORTAL);
        assertThat(bindings.findByUsername("portal-a").orElseThrow().customerId()).isEqualTo(customerId);
        AuthUserAccount account = accounts.findByUsername("portal-a").orElseThrow();
        loginSessions.save(new AuthLoginSession(
                AuthTokenDigest.sha256("jdbc-token"), account.username(), account.operator(), account.role().name(),
                Instant.now().plusSeconds(3600), AuthAccountSnapshot.fingerprint(account), "token"));
        assertThat(service.disable(admin(), "portal-a")).isEqualTo(1);
        assertThat(accounts.findByUsername("portal-a").orElseThrow().enabled()).isFalse();
        assertThat(bindings.findByUsername("portal-a").orElseThrow().enabled()).isFalse();
        assertThat(loginSessions.findByTokenHash(AuthTokenDigest.sha256("jdbc-token"))).isEmpty();

        AuthUserAccount first = portalAccount("portal-b", otherId);
        AuthUserAccount second = portalAccount("portal-c", otherId);
        actions.commit(action(first, otherId, "audit-b"));
        assertThatThrownBy(() -> actions.commit(action(second, otherId, "audit-c")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("本版一个客户只绑定一个账号");
        assertThat(accounts.findByUsername("portal-c")).isEmpty();
        assertThat(bindings.findByUsername("portal-b").orElseThrow().customerId()).isEqualTo(otherId);
        assertThat(countAccounts(dataSource, "portal-c")).isZero();
    }

    private static Fixture memory() {
        var accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        var bindings = new InMemoryCustomerPortalBindingRepository();
        var customers = new InMemoryMasterDataRepository();
        long customerId = customers.saveCustomer(
                new CustomerProfile(0, "HX", "恒信珠宝", "", "", "", "", true)).customerId();
        var loginSessions = new InMemoryAuthLoginSessionRepository();
        var sessions = sessionsFor(accounts, bindings, loginSessions);
        var service = new CustomerPortalIdentityService(accounts, bindings, customers,
                new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                sessions, new PasswordHasher(), new PasswordPolicy());
        return new Fixture(service, accounts, bindings, customers, sessions, loginSessions, new PermissionGuard(),
                admin(), customerId);
    }

    private static AuthSessionService sessionsFor(AuthUserAccountRepository accounts,
            InMemoryCustomerPortalBindingRepository bindings) {
        return sessionsFor(accounts, bindings, new InMemoryAuthLoginSessionRepository());
    }

    private static AuthSessionService sessionsFor(AuthUserAccountRepository accounts,
            com.zhubao.erp.security.domain.CustomerPortalBindingRepository bindings,
            InMemoryAuthLoginSessionRepository loginSessions) {
        AuthSessionService sessions = new AuthSessionService(
                new NavigationMenuService(new InMemoryNavigationMenuRepository()),
                new InMemoryAuthAuditRepository(),
                accounts,
                new PasswordHasher(),
                new PasswordExpiryPolicy(),
                new PasswordPolicy(),
                null,
                loginSessions,
                new PermissionGuard());
        sessions.setCustomerPortalBindings(bindings);
        return sessions;
    }

    private static OperatorContext admin() {
        return new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "", List.of());
    }

    private static AuthUserAccount internal(String username, RoleCode role) {
        return new AuthUserAccount(username, "hash", username, role, DataScope.ALL, true, false, List.of(CoverDuties.WAREHOUSE));
    }

    private static AuthUserAccount portalAccount(String username, long customerId) {
        return new AuthUserAccount(username, CustomerPortalRules.UNISSUED_PASSWORD, "客户", RoleCode.CUSTOMER_PORTAL,
                DataScope.CUSTOMER_RANGE, true, false, Instant.now(), 0, null,
                List.of(Long.toString(customerId)), false, false, List.of(), List.of());
    }

    private static CustomerPortalIdentityAction action(AuthUserAccount account, long customerId, String auditId) {
        return new CustomerPortalIdentityAction(account,
                new CustomerPortalBinding(account.username(), customerId, true, "系统管理员", "2026-10-31 00:00:00"),
                new AuthAuditEvent(auditId, "CUSTOMER_PORTAL", account.username(), "系统管理员", "ADMIN",
                        "SUCCESS", "绑定稳定客户主体 " + customerId, "2026-10-31 00:00:00", ""));
    }

    private static int countAccounts(JdbcDataSource dataSource, String username) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select count(*) from auth_user_account where username = '" + username + "'")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void createSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
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
            for (String sql : MigrationCatalog.statements(Files.readString(
                    Path.of("src/main/resources/db/mysql/V106__m3_customer_portal_binding.sql"), StandardCharsets.UTF_8))) {
                statement.execute(sql);
            }
        }
    }

    private record Fixture(
            CustomerPortalIdentityService service,
            AuthUserAccountRepository accounts,
            InMemoryCustomerPortalBindingRepository bindings,
            MasterDataRepository customers,
            AuthSessionService sessions,
            InMemoryAuthLoginSessionRepository loginSessions,
            PermissionGuard guard,
            OperatorContext admin,
            long customerId
    ) {
    }
}
