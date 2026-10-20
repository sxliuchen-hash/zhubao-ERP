package com.zhubao.erp.security.infra;

import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.CoverDuties;
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
import java.util.Arrays;
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
                    password_changed_at, failed_login_count, locked_until, customer_scope, password_change_required,
                    temporary_password_consumed, allowed_processes, cover_duties
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            statement.setString(11, serializeCustomerScope(account.customerScope()));
            statement.setBoolean(12, account.passwordChangeRequired());
            statement.setBoolean(13, account.temporaryPasswordConsumed());
            statement.setString(14, WorkshopProcesses.join(account.allowedProcesses()));
            statement.setString(15, CoverDuties.join(account.coverDuties()));
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
                       locked_until = ?,
                       customer_scope = ?,
                       password_change_required = ?,
                       temporary_password_consumed = ?,
                       allowed_processes = ?,
                       cover_duties = ?
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
            statement.setString(10, serializeCustomerScope(account.customerScope()));
            statement.setBoolean(11, account.passwordChangeRequired());
            statement.setBoolean(12, account.temporaryPasswordConsumed());
            statement.setString(13, WorkshopProcesses.join(account.allowedProcesses()));
            statement.setString(14, CoverDuties.join(account.coverDuties()));
            statement.setString(15, normalize(account.username()));
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
                       password_changed_at, failed_login_count, locked_until, customer_scope, password_change_required,
                       temporary_password_consumed, allowed_processes, cover_duties
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
                       password_changed_at, failed_login_count, locked_until, customer_scope, password_change_required,
                       temporary_password_consumed, allowed_processes, cover_duties
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

    @Override
    public boolean consumeTemporaryPassword(String username) {
        String sql = """
                update auth_user_account
                   set temporary_password_consumed = true
                 where username = ?
                   and password_change_required = true
                   and temporary_password_consumed = false
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to consume auth user temporary password", ex);
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
                readInstant(rs, "locked_until"),
                parseCustomerScope(rs.getString("customer_scope")),
                rs.getBoolean("password_change_required"),
                rs.getBoolean("temporary_password_consumed"),
                WorkshopProcesses.bind(WorkshopProcesses.split(rs.getString("allowed_processes"))),
                CoverDuties.split(rs.getString("cover_duties"))
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

    private String serializeCustomerScope(List<String> customerScope) {
        return customerScope == null ? "" : String.join("|", customerScope);
    }

    private List<String> parseCustomerScope(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[|,，;；]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 在调用方的事务连接上写入账号。影响行数为 0 时由调用方回滚。
     */
    void apply(Connection connection, com.zhubao.erp.security.domain.AuthAccountMutation mutation) throws SQLException {
        switch (mutation.kind()) {
            case SAVE -> write(connection, mutation.account());
            case SET_ENABLED -> {
                if (updateEnabledOn(connection, mutation.username(), Boolean.TRUE.equals(mutation.enabled())) != 1) {
                    throw new com.zhubao.erp.common.error.BusinessException("账号不存在：" + mutation.username());
                }
            }
            case UNLOCK -> {
                if (unlockOn(connection, mutation.username()) != 1) {
                    throw new com.zhubao.erp.common.error.BusinessException("账号不存在：" + mutation.username());
                }
            }
            case DELETE -> {
                try (PreparedStatement statement = connection.prepareStatement("delete from auth_user_account where username = ?")) {
                    statement.setString(1, normalize(mutation.username()));
                    if (statement.executeUpdate() != 1) {
                        throw new com.zhubao.erp.common.error.BusinessException("账号不存在：" + mutation.username());
                    }
                }
            }
        }
    }

    /** 锁住启用管理员集合后检查，防止并发停用或降权把最后的管理员全部移除。 */
    void assertAdminRemains(Connection connection, com.zhubao.erp.security.domain.AuthAccountMutation mutation) throws SQLException {
        if (!mutation.removesEnabledAdmin()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "select username from auth_user_account where role_code = 'ADMIN' and enabled = true order by username for update");
             ResultSet result = statement.executeQuery()) {
            int count = 0;
            boolean targetAdmin = false;
            while (result.next()) {
                count++;
                targetAdmin |= normalize(mutation.username()).equals(result.getString("username"));
            }
            if (targetAdmin && count <= 1) {
                throw new com.zhubao.erp.common.error.BusinessException("必须保留至少一个启用的系统管理员");
            }
        }
    }

    private void write(Connection connection, AuthUserAccount account) throws SQLException {
        if (account == null) {
            throw new com.zhubao.erp.common.error.BusinessException("保存账号缺少账号资料");
        }
        if (exists(connection, account.username())) {
            updateOn(connection, account);
            return;
        }
        insertOn(connection, account);
    }

    private boolean exists(Connection connection, String username) throws SQLException {
        String sql = "select username from auth_user_account where username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertOn(Connection connection, AuthUserAccount account) throws SQLException {
        String sql = """
                insert into auth_user_account (
                    username, password_hash, operator, role_code, data_scope, enabled, demo_account,
                    password_changed_at, failed_login_count, locked_until, customer_scope, password_change_required,
                    temporary_password_consumed, allowed_processes, cover_duties
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
            statement.setString(11, serializeCustomerScope(account.customerScope()));
            statement.setBoolean(12, account.passwordChangeRequired());
            statement.setBoolean(13, account.temporaryPasswordConsumed());
            statement.setString(14, WorkshopProcesses.join(account.allowedProcesses()));
            statement.setString(15, CoverDuties.join(account.coverDuties()));
            statement.executeUpdate();
        }
    }

    private void updateOn(Connection connection, AuthUserAccount account) throws SQLException {
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
                       locked_until = ?,
                       customer_scope = ?,
                       password_change_required = ?,
                       temporary_password_consumed = ?,
                       allowed_processes = ?,
                       cover_duties = ?
                 where username = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.passwordHash());
            statement.setString(2, account.operator());
            statement.setString(3, account.role().name());
            statement.setString(4, account.dataScope().name());
            statement.setBoolean(5, account.enabled());
            statement.setBoolean(6, account.demoAccount());
            setInstant(statement, 7, account.passwordChangedAt());
            statement.setInt(8, account.failedLoginCount());
            setInstant(statement, 9, account.lockedUntil());
            statement.setString(10, serializeCustomerScope(account.customerScope()));
            statement.setBoolean(11, account.passwordChangeRequired());
            statement.setBoolean(12, account.temporaryPasswordConsumed());
            statement.setString(13, WorkshopProcesses.join(account.allowedProcesses()));
            statement.setString(14, CoverDuties.join(account.coverDuties()));
            statement.setString(15, normalize(account.username()));
            if (statement.executeUpdate() != 1) {
                throw new com.zhubao.erp.common.error.BusinessException("账号不存在：" + account.username());
            }
        }
    }

    private int updateEnabledOn(Connection connection, String username, boolean enabled) throws SQLException {
        String sql = """
                update auth_user_account
                   set enabled = ?
                 where username = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setString(2, normalize(username));
            return statement.executeUpdate();
        }
    }

    private int unlockOn(Connection connection, String username) throws SQLException {
        String sql = """
                update auth_user_account
                   set failed_login_count = 0,
                       locked_until = null
                 where username = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            return statement.executeUpdate();
        }
    }
}
