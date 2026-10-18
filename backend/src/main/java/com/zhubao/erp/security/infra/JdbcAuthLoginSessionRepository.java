package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthLoginSession;
import com.zhubao.erp.security.domain.AuthLoginSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.users.repository", havingValue = "jdbc")
public class JdbcAuthLoginSessionRepository implements AuthLoginSessionRepository {

    private final DataSource dataSource;

    public JdbcAuthLoginSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(AuthLoginSession session) {
        String sql = """
                insert into auth_login_session (
                    token_hash, username, operator, role_code, expires_at, account_fingerprint, token_suffix, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.tokenHash());
            statement.setString(2, session.username());
            statement.setString(3, session.operator());
            statement.setString(4, session.role());
            statement.setString(5, session.expiresAt().toString());
            statement.setString(6, session.accountFingerprint());
            statement.setString(7, session.tokenSuffix());
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("保存登录会话失败", ex);
        }
    }

    @Override
    public Optional<AuthLoginSession> findByTokenHash(String tokenHash) {
        String sql = """
                select token_hash, username, operator, role_code, expires_at, account_fingerprint, token_suffix
                  from auth_login_session
                 where token_hash = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取登录会话失败", ex);
        }
    }

    @Override
    public void updateExpiry(String tokenHash, Instant expiresAt) {
        updateColumn(tokenHash, "expires_at", expiresAt.toString());
    }

    @Override
    public boolean updateFingerprint(String tokenHash, String accountFingerprint) {
        return updateColumn(tokenHash, "account_fingerprint", accountFingerprint);
    }

    @Override
    public Optional<AuthLoginSession> removeByTokenHash(String tokenHash) {
        Optional<AuthLoginSession> existing = findByTokenHash(tokenHash);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        String sql = "delete from auth_login_session where token_hash = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            return statement.executeUpdate() == 1 ? existing : Optional.empty();
        } catch (SQLException ex) {
            throw new IllegalStateException("删除登录会话失败", ex);
        }
    }

    @Override
    public List<AuthLoginSession> removeByUsername(String username) {
        String normalized = normalize(username);
        List<AuthLoginSession> removed = new ArrayList<>();
        if (normalized.isBlank()) {
            return removed;
        }
        String sql = """
                select token_hash, username, operator, role_code, expires_at, account_fingerprint, token_suffix
                  from auth_login_session
                 where lower(username) = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    AuthLoginSession session = map(result);
                    removeByTokenHash(session.tokenHash()).ifPresent(removed::add);
                }
            }
            return removed;
        } catch (SQLException ex) {
            throw new IllegalStateException("吊销登录会话失败", ex);
        }
    }

    private boolean updateColumn(String tokenHash, String column, String value) {
        String sql = "update auth_login_session set " + column + " = ? where token_hash = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setString(2, tokenHash);
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new IllegalStateException("更新登录会话失败", ex);
        }
    }

    private AuthLoginSession map(ResultSet result) throws SQLException {
        return new AuthLoginSession(
                result.getString("token_hash"),
                result.getString("username"),
                result.getString("operator"),
                result.getString("role_code"),
                Instant.parse(result.getString("expires_at")),
                result.getString("account_fingerprint"),
                result.getString("token_suffix")
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
