package com.zhubao.erp.security.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthUserAccountRepository {

    AuthUserAccount save(AuthUserAccount account);

    Optional<AuthUserAccount> findByUsername(String username);

    List<String> findActiveUsernames();

    void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil);

    void recordLoginSuccess(String username);
}
