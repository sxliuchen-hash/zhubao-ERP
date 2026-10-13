package com.zhubao.erp.security.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthUserAccountRepository {

    AuthUserAccount save(AuthUserAccount account);

    default void delete(String username) {
        throw new UnsupportedOperationException("当前账号仓储不支持删除");
    }

    List<AuthUserAccount> findAll();

    Optional<AuthUserAccount> findByUsername(String username);

    List<String> findActiveUsernames();

    void updateEnabled(String username, boolean enabled);

    void unlock(String username);

    void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil);

    void recordLoginSuccess(String username);

    boolean consumeTemporaryPassword(String username);
}
