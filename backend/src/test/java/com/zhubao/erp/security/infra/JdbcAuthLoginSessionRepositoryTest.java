package com.zhubao.erp.security.infra;

import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.domain.AuthAccountSnapshot;
import com.zhubao.erp.security.domain.AuthLoginSession;
import com.zhubao.erp.security.domain.AuthTokenDigest;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuthLoginSessionRepositoryTest {

    private JdbcDataSource dataSource;
    private JdbcAuthLoginSessionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        String sql = new String(
                getClass().getClassLoader().getResourceAsStream("db/mysql/V63__m3_auth_login_session.sql").readAllBytes(),
                StandardCharsets.UTF_8
        );
        for (String statement : MigrationCatalog.statements(sql)) {
            try (Connection connection = dataSource.getConnection();
                 Statement command = connection.createStatement()) {
                command.execute(statement);
            }
        }
        repository = new JdbcAuthLoginSessionRepository(dataSource);
    }

    @Test
    void 新仓储实例仍能按摘要恢复且库里没有原始令牌() throws Exception {
        AuthUserAccount account = new AuthUserAccount(
                "operator", "pbkdf2-should-not-be-stored", "操作工 王强",
                RoleCode.OPERATOR, DataScope.SELF, true, false
        );
        String rawToken = "raw-token-should-stay-on-device";
        AuthLoginSession session = session(account, rawToken, Instant.parse("2026-11-15T00:00:00Z"));
        repository.save(session);

        JdbcAuthLoginSessionRepository restarted = new JdbcAuthLoginSessionRepository(dataSource);
        assertThat(restarted.findByTokenHash(session.tokenHash())).contains(session);
        assertThat(restarted.findByTokenHash(rawToken)).isEmpty();
        try (Connection connection = dataSource.getConnection();
             Statement command = connection.createStatement();
             ResultSet result = command.executeQuery("select token_hash, account_fingerprint, token_suffix from auth_login_session")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("token_hash")).isEqualTo(AuthTokenDigest.sha256(rawToken)).doesNotContain(rawToken);
            assertThat(result.getString("account_fingerprint")).doesNotContain("pbkdf2");
            assertThat(result.getString("token_suffix")).isEqualTo("device");
        }

        assertThat(restarted.removeByTokenHash(session.tokenHash())).contains(session);
        assertThat(restarted.removeByTokenHash(session.tokenHash())).isEmpty();
        assertThat(restarted.findByTokenHash(session.tokenHash())).isEmpty();
    }

    @Test
    void 可顺延快照并按账号一次吊销() {
        AuthUserAccount operator = new AuthUserAccount(
                "operator", "hash-a", "操作工", RoleCode.OPERATOR, DataScope.SELF, true, false
        );
        AuthUserAccount leader = new AuthUserAccount(
                "Leader", "hash-b", "组长", RoleCode.WORKSHOP_LEADER, DataScope.TEAM, true, false
        );
        repository.save(session(operator, "token-operator-1", Instant.parse("2026-11-01T00:00:00Z")));
        repository.save(session(operator, "token-operator-2", Instant.parse("2026-11-02T00:00:00Z")));
        AuthLoginSession leaderSession = session(leader, "token-leader", Instant.parse("2026-11-03T00:00:00Z"));
        repository.save(leaderSession);

        Instant renewed = Instant.parse("2026-12-01T00:00:00Z");
        repository.updateExpiry(AuthTokenDigest.sha256("token-operator-1"), renewed);
        assertThat(repository.findByTokenHash(AuthTokenDigest.sha256("token-operator-1")).orElseThrow().expiresAt())
                .isEqualTo(renewed);
        assertThat(repository.updateFingerprint(leaderSession.tokenHash(), "new-fingerprint")).isTrue();
        assertThat(repository.findByTokenHash(leaderSession.tokenHash()).orElseThrow().accountFingerprint())
                .isEqualTo("new-fingerprint");

        assertThat(repository.removeByUsername("OPERATOR")).hasSize(2);
        assertThat(repository.findByTokenHash(AuthTokenDigest.sha256("token-operator-1"))).isEmpty();
        assertThat(repository.findByTokenHash(leaderSession.tokenHash())).isPresent();
    }

    private AuthLoginSession session(AuthUserAccount account, String rawToken, Instant expiresAt) {
        return new AuthLoginSession(
                AuthTokenDigest.sha256(rawToken),
                account.username(),
                account.operator(),
                account.role().name(),
                expiresAt,
                AuthAccountSnapshot.fingerprint(account),
                rawToken.substring(rawToken.length() - 6)
        );
    }
}
