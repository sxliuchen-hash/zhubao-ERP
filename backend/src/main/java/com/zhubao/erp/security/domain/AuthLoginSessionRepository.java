package com.zhubao.erp.security.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 登录会话存储。内存实现随进程丢失；JDBC 实现随账号库保留，服务重启后仍可恢复。
 */
public interface AuthLoginSessionRepository {

    void save(AuthLoginSession session);

    Optional<AuthLoginSession> findByTokenHash(String tokenHash);

    void updateExpiry(String tokenHash, Instant expiresAt);

    boolean updateFingerprint(String tokenHash, String accountFingerprint);

    Optional<AuthLoginSession> removeByTokenHash(String tokenHash);

    List<AuthLoginSession> removeByUsername(String username);
}
