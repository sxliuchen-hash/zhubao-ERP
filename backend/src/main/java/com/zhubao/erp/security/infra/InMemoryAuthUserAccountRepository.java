package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.users.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthUserAccountRepository implements AuthUserAccountRepository {

    private final Map<String, AuthUserAccount> accounts = new ConcurrentHashMap<>();

    public InMemoryAuthUserAccountRepository(PasswordHasher passwordHasher) {
        this(passwordHasher, "admin123");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InMemoryAuthUserAccountRepository(PasswordHasher hasher,
            @org.springframework.beans.factory.annotation.Value("${zhubao.auth.initial-admin-password:admin123}") String initialPassword) {
        save(new AuthUserAccount("admin", hasher.hash(initialPassword), "系统管理员", RoleCode.ADMIN, DataScope.ALL,
                true, false, Instant.now(), 0, null, List.of(), true, false, List.of()));
    }

    @Override
    public AuthUserAccount save(AuthUserAccount account) {
        accounts.put(normalize(account.username()), account);
        return account;
    }

    /**
     * 删除账号凭据；业务、审批及审计记录保持独立保留。
     */
    @Override
    public void delete(String username) {
        accounts.remove(normalize(username));
    }

    @Override
    public List<AuthUserAccount> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(AuthUserAccount::username))
                .toList();
    }

    @Override
    public Optional<AuthUserAccount> findByUsername(String username) {
        return Optional.ofNullable(accounts.get(normalize(username)));
    }

    @Override
    public List<String> findActiveUsernames() {
        return accounts.values().stream()
                .filter(AuthUserAccount::enabled)
                .map(AuthUserAccount::username)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public void updateEnabled(String username, boolean enabled) {
        accounts.computeIfPresent(normalize(username), (key, account) -> new AuthUserAccount(
                account.username(),
                account.passwordHash(),
                account.operator(),
                account.role(),
                account.dataScope(),
                enabled,
                account.demoAccount(),
                account.passwordChangedAt(),
                account.failedLoginCount(),
                account.lockedUntil(),
                account.customerScope(),
                account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(),
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode()
        ));
    }

    @Override
    public void unlock(String username) {
        accounts.computeIfPresent(normalize(username), (key, account) -> new AuthUserAccount(
                account.username(),
                account.passwordHash(),
                account.operator(),
                account.role(),
                account.dataScope(),
                account.enabled(),
                account.demoAccount(),
                account.passwordChangedAt(),
                0,
                null,
                account.customerScope(),
                account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(),
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode()
        ));
    }

    @Override
    public void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil) {
        accounts.computeIfPresent(normalize(username), (key, account) -> new AuthUserAccount(
                account.username(),
                account.passwordHash(),
                account.operator(),
                account.role(),
                account.dataScope(),
                account.enabled(),
                account.demoAccount(),
                account.passwordChangedAt(),
                failedLoginCount,
                lockedUntil,
                account.customerScope(),
                account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(),
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode()
        ));
    }

    @Override
    public void recordLoginSuccess(String username) {
        accounts.computeIfPresent(normalize(username), (key, account) -> new AuthUserAccount(
                account.username(),
                account.passwordHash(),
                account.operator(),
                account.role(),
                account.dataScope(),
                account.enabled(),
                account.demoAccount(),
                account.passwordChangedAt(),
                0,
                null,
                account.customerScope(),
                account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(),
                account.allowedProcesses(),
                account.coverDuties(),
                account.teamCode(),
                account.departmentCode()
        ));
    }

    @Override
    public boolean consumeTemporaryPassword(String username) {
        AtomicBoolean consumed = new AtomicBoolean(false);
        accounts.computeIfPresent(normalize(username), (key, account) -> {
            if (!account.passwordChangeRequired() || account.temporaryPasswordConsumed()) {
                return account;
            }
            consumed.set(true);
            return new AuthUserAccount(
                    account.username(),
                    account.passwordHash(),
                    account.operator(),
                    account.role(),
                    account.dataScope(),
                    account.enabled(),
                    account.demoAccount(),
                    account.passwordChangedAt(),
                    account.failedLoginCount(),
                    account.lockedUntil(),
                    account.customerScope(),
                    true,
                    true,
                    account.allowedProcesses(),
                    account.coverDuties(),
                    account.teamCode(),
                    account.departmentCode()
            );
        });
        return consumed.get();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
