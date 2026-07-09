package com.zhubao.erp.security.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthUserAccountRepository {

    AuthUserAccount save(AuthUserAccount account);

    List<AuthUserAccount> findAll();

    Optional<AuthUserAccount> findByUsername(String username);

    List<String> findActiveUsernames();

    void updateEnabled(String username, boolean enabled);

    void unlock(String username);

    void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil);

    void recordLoginSuccess(String username);

    boolean consumeTemporaryPassword(String username);
}
