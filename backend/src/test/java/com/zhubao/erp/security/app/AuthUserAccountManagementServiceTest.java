package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserAccountManagementServiceTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordExpiryPolicy passwordExpiryPolicy = new PasswordExpiryPolicy();
    private final NavigationMenuService navigationMenuService = new NavigationMenuService();
    private final OperatorContext admin = new OperatorContext(
            "系统管理员",
            RoleCode.ADMIN,
            DataScope.ALL,
            false,
            "admin-token-abcdef"
    );

    @Test
    void createsProductionAccountThatCanLogin() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "Planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        AuthSession session = fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!"));

        assertThat(session.username()).isEqualTo("planner");
        assertThat(session.operator()).isEqualTo("计划员 陈敏");
        assertThat(session.role()).isEqualTo("SALES");
        assertThat(session.demoAccount()).isFalse();
    }

    @Test
    void updatesAccountWithoutPasswordKeepsExistingPasswordHash() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        String originalHash = fixture.accountRepository().findByUsername("planner").orElseThrow().passwordHash();
        Instant originalPasswordChangedAt = fixture.accountRepository().findByUsername("planner").orElseThrow().passwordChangedAt();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                null,
                "计划员 陈敏-调整",
                "WAREHOUSE_KEEPER",
                "DEPARTMENT",
                true,
                false
        ), admin);

        AuthUserAccount updated = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(updated.passwordHash()).isEqualTo(originalHash);
        assertThat(updated.passwordChangedAt()).isEqualTo(originalPasswordChangedAt);
        assertThat(updated.operator()).isEqualTo("计划员 陈敏-调整");
        assertThat(updated.role()).isEqualTo(RoleCode.WAREHOUSE_KEEPER);
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")).role())
                .isEqualTo("WAREHOUSE_KEEPER");
    }

    @Test
    void resettingPasswordClearsFailedLoginLockout() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Old!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        Instant oldPasswordChangedAt = Instant.now().minus(Duration.ofDays(91));
        AuthUserAccount oldAccount = fixture.accountRepository().findByUsername("planner").orElseThrow();
        fixture.accountRepository().save(new AuthUserAccount(
                oldAccount.username(),
                oldAccount.passwordHash(),
                oldAccount.operator(),
                oldAccount.role(),
                oldAccount.dataScope(),
                oldAccount.enabled(),
                oldAccount.demoAccount(),
                oldPasswordChangedAt,
                oldAccount.failedLoginCount(),
                oldAccount.lockedUntil()
        ));
        fixture.accountRepository().recordLoginFailure("planner", 5, Instant.now().plusSeconds(900));

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-New!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        AuthUserAccount account = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockedUntil()).isNull();
        assertThat(account.passwordChangedAt()).isAfter(oldPasswordChangedAt);
        assertThatThrownBy(() -> fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Old!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-New!")).username())
                .isEqualTo("planner");
    }

    @Test
    void accountSummaryExposesPasswordExpiryState() {
        Fixture fixture = fixture();
        Instant oldPasswordChangedAt = Instant.now().minus(Duration.ofDays(91));
        fixture.accountRepository().save(new AuthUserAccount(
                "planner",
                passwordHasher.hash("Secure-2026-Old!"),
                "计划员 陈敏",
                RoleCode.SALES,
                DataScope.CUSTOMER_RANGE,
                true,
                false,
                oldPasswordChangedAt,
                0,
                null
        ));

        assertThat(fixture.managementService().accounts())
                .filteredOn(account -> account.username().equals("planner"))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.passwordChangedAt()).isNotBlank();
                    assertThat(account.passwordExpiresAt()).isNotBlank();
                    assertThat(account.passwordExpired()).isTrue();
                });
    }

    @Test
    void rejectsWeakPasswordThroughManagementBoundary() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "admin123",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("常见弱口令");
        assertThat(fixture.accountRepository().findByUsername("planner")).isEmpty();
    }

    @Test
    void disabledAccountCannotLoginAndUsesGenericMessage() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);

        fixture.managementService().updateEnabled("planner", false, "离职交接", admin);

        assertThat(fixture.accountRepository().findByUsername("planner")).get()
                .extracting(AuthUserAccount::enabled)
                .isEqualTo(false);
        assertThatThrownBy(() -> fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void adminUnlockClearsLockoutAndAllowsCorrectPasswordLogin() {
        Fixture fixture = fixture();
        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        fixture.accountRepository().recordLoginFailure("planner", 5, Instant.now().plusSeconds(900));

        fixture.managementService().unlock("planner", "现场核实本人", admin);

        AuthUserAccount account = fixture.accountRepository().findByUsername("planner").orElseThrow();
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockedUntil()).isNull();
        assertThat(fixture.sessionService().login(new AuthLoginCommand("planner", "Secure-2026-Plan!")).username())
                .isEqualTo("planner");
    }

    @Test
    void writesAccountManagementAuditEvents() {
        Fixture fixture = fixture();

        fixture.managementService().upsert(new ManageAuthUserAccountCommand(
                "planner",
                "Secure-2026-Plan!",
                "计划员 陈敏",
                "SALES",
                "CUSTOMER_RANGE",
                true,
                false
        ), admin);
        fixture.managementService().updateEnabled("planner", false, "权限回收", admin);
        fixture.managementService().unlock("planner", "误锁恢复", admin);

        assertThat(fixture.auditRepository().findRecent(10))
                .extracting("eventType")
                .contains("ACCOUNT_MANAGEMENT");
        assertThat(fixture.auditRepository().findRecent(10))
                .anySatisfy(event -> {
                    assertThat(event.eventId()).startsWith("AUTH-ACC-");
                    assertThat(event.username()).isEqualTo("planner");
                    assertThat(event.operator()).isEqualTo("系统管理员");
                    assertThat(event.role()).isEqualTo("ADMIN");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.sessionTokenSuffix()).isEqualTo("abcdef");
                });
    }

    private Fixture fixture() {
        InMemoryAuthAuditRepository auditRepository = new InMemoryAuthAuditRepository();
        InMemoryAuthUserAccountRepository accountRepository = new InMemoryAuthUserAccountRepository(passwordHasher);
        AuthUserAccountManagementService managementService = new AuthUserAccountManagementService(
                accountRepository,
                passwordHasher,
                passwordPolicy,
                passwordExpiryPolicy,
                auditRepository
        );
        AuthSessionService sessionService = new AuthSessionService(
                navigationMenuService,
                auditRepository,
                accountRepository,
                passwordHasher,
                passwordExpiryPolicy
        );
        return new Fixture(accountRepository, auditRepository, managementService, sessionService);
    }

    private record Fixture(
            AuthUserAccountRepository accountRepository,
            InMemoryAuthAuditRepository auditRepository,
            AuthUserAccountManagementService managementService,
            AuthSessionService sessionService
    ) {
    }
}
