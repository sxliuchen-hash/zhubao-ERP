package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthLoginSession;
import com.zhubao.erp.security.domain.AuthLoginSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.users.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthLoginSessionRepository implements AuthLoginSessionRepository {

    private final ConcurrentHashMap<String, AuthLoginSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AuthLoginSession session) {
        sessions.put(session.tokenHash(), session);
    }

    @Override
    public Optional<AuthLoginSession> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void updateExpiry(String tokenHash, Instant expiresAt) {
        sessions.computeIfPresent(tokenHash, (key, current) -> copy(current, expiresAt, current.accountFingerprint()));
    }

    @Override
    public boolean updateFingerprint(String tokenHash, String accountFingerprint) {
        return sessions.computeIfPresent(tokenHash, (key, current) -> copy(current, current.expiresAt(), accountFingerprint)) != null;
    }

    @Override
    public Optional<AuthLoginSession> removeByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessions.remove(tokenHash));
    }

    @Override
    public List<AuthLoginSession> removeByUsername(String username) {
        String normalized = normalize(username);
        List<AuthLoginSession> removed = new ArrayList<>();
        if (normalized.isBlank()) {
            return removed;
        }
        for (AuthLoginSession session : List.copyOf(sessions.values())) {
            if (normalized.equals(normalize(session.username())) && sessions.remove(session.tokenHash(), session)) {
                removed.add(session);
            }
        }
        return removed;
    }

    private AuthLoginSession copy(AuthLoginSession current, Instant expiresAt, String accountFingerprint) {
        return new AuthLoginSession(
                current.tokenHash(),
                current.username(),
                current.operator(),
                current.role(),
                expiresAt,
                accountFingerprint,
                current.tokenSuffix()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
