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
        save(demo("admin", "admin123", "系统管理员", RoleCode.ADMIN, DataScope.ALL, passwordHasher));
        save(demo("leader", "leader123", "车间组长 马杰", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, passwordHasher));
        save(demo("qc", "qc123", "质检员 周娜", RoleCode.QUALITY_INSPECTOR, DataScope.DEPARTMENT, passwordHasher));
        save(demo("warehouse", "warehouse123", "库管员 何琳", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, passwordHasher));
        save(demo("operator", "operator123", "操作工 王强", RoleCode.OPERATOR, DataScope.SELF, passwordHasher));
        save(demo("carve", "carve123", "雕刻工 林海", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("雕刻")));
        save(demo("inlay", "inlay123", "镶嵌工 赵敏", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("镶嵌")));
        save(demo("polish", "polish123", "抛光工 陈刚", RoleCode.OPERATOR, DataScope.SELF, passwordHasher)
                .withAllowedProcesses(List.of("抛光")));
    }

    @Override
    public AuthUserAccount save(AuthUserAccount account) {
        accounts.put(normalize(account.username()), account);
        return account;
    }

    /**
     * 撤销尚未提交完成的新账号。只供同一包内的账号动作补偿使用。
     */
    void delete(String username) {
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
                account.allowedProcesses()
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
                account.allowedProcesses()
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
                account.allowedProcesses()
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
                account.allowedProcesses()
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
                    account.allowedProcesses()
            );
        });
        return consumed.get();
    }

    private AuthUserAccount demo(
            String username,
            String password,
            String operator,
            RoleCode role,
            DataScope dataScope,
            PasswordHasher passwordHasher
    ) {
        return new AuthUserAccount(username, passwordHasher.hash(password), operator, role, dataScope, true, true);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
