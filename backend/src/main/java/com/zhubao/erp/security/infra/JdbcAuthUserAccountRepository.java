package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.users.repository", havingValue = "jdbc")
public class JdbcAuthUserAccountRepository implements AuthUserAccountRepository {

    private final DataSource dataSource;

    public JdbcAuthUserAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AuthUserAccount save(AuthUserAccount account) {
        if (findByUsername(account.username()).isPresent()) {
            return update(account);
        }
        return insert(account);
    }

    private AuthUserAccount insert(AuthUserAccount account) {
        String sql = """
                insert into auth_user_account (
                    username, password_hash, operator, role_code, data_scope, enabled, demo_account,
                    password_changed_at, failed_login_count, locked_until
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(account.username()));
            statement.setString(2, account.passwordHash());
            statement.setString(3, account.operator());
            statement.setString(4, account.role().name());
            statement.setString(5, account.dataScope().name());
            statement.setBoolean(6, account.enabled());
            statement.setBoolean(7, account.demoAccount());
            setInstant(statement, 8, account.passwordChangedAt());
            statement.setInt(9, account.failedLoginCount());
            setInstant(statement, 10, account.lockedUntil());
            statement.executeUpdate();
            return account;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save auth user account", ex);
        }
    }

    private AuthUserAccount update(AuthUserAccount account) {
        String sql = """
                update auth_user_account
                   set password_hash = ?,
                       operator = ?,
                       role_code = ?,
                       data_scope = ?,
                       enabled = ?,
                       demo_account = ?,
                       password_changed_at = ?,
                       failed_login_count = ?,
                       locked_until = ?
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.passwordHash());
            statement.setString(2, account.operator());
            statement.setString(3, account.role().name());
            statement.setString(4, account.dataScope().name());
            statement.setBoolean(5, account.enabled());
            statement.setBoolean(6, account.demoAccount());
            setInstant(statement, 7, account.passwordChangedAt());
            statement.setInt(8, account.failedLoginCount());
            setInstant(statement, 9, account.lockedUntil());
            statement.setString(10, normalize(account.username()));
            statement.executeUpdate();
            return account;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update auth user account", ex);
        }
    }

    @Override
    public List<AuthUserAccount> findAll() {
        String sql = """
                select username, password_hash, operator, role_code, data_scope, enabled, demo_account,
                       password_changed_at, failed_login_count, locked_until
                  from auth_user_account
                 order by username
                """;
        List<AuthUserAccount> accounts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                accounts.add(map(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth user accounts", ex);
        }
        return accounts;
    }

    @Override
    public Optional<AuthUserAccount> findByUsername(String username) {
        String sql = """
                select username, password_hash, operator, role_code, data_scope, enabled, demo_account,
                       password_changed_at, failed_login_count, locked_until
                  from auth_user_account
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth user account", ex);
        }
        return Optional.empty();
    }

    @Override
    public List<String> findActiveUsernames() {
        String sql = """
                select username
                  from auth_user_account
                 where enabled = true
                 order by username
                """;
        List<String> usernames = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load auth user accounts", ex);
        }
        return usernames;
    }

    @Override
    public void updateEnabled(String username, boolean enabled) {
        String sql = """
                update auth_user_account
                   set enabled = ?
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setString(2, normalize(username));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update auth user account status", ex);
        }
    }

    @Override
    public void unlock(String username) {
        String sql = """
                update auth_user_account
                   set failed_login_count = 0,
                       locked_until = null
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to unlock auth user account", ex);
        }
    }

    @Override
    public void recordLoginFailure(String username, int failedLoginCount, Instant lockedUntil) {
        String sql = """
                update auth_user_account
                   set failed_login_count = ?,
                       locked_until = ?,
                       last_failed_login_at = current_timestamp
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, failedLoginCount);
            setInstant(statement, 2, lockedUntil);
            statement.setString(3, normalize(username));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to record auth user login failure", ex);
        }
    }

    @Override
    public void recordLoginSuccess(String username) {
        String sql = """
                update auth_user_account
                   set failed_login_count = 0,
                       locked_until = null,
                       last_login_at = current_timestamp
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to record auth user login success", ex);
        }
    }

    private AuthUserAccount map(ResultSet rs) throws SQLException {
        return new AuthUserAccount(
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("operator"),
                RoleCode.valueOf(rs.getString("role_code")),
                DataScope.valueOf(rs.getString("data_scope")),
                rs.getBoolean("enabled"),
                rs.getBoolean("demo_account"),
                readInstant(rs, "password_changed_at"),
                rs.getInt("failed_login_count"),
                readInstant(rs, "locked_until")
        );
    }

    private Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setTimestamp(index, null);
            return;
        }
        statement.setTimestamp(index, Timestamp.from(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
