package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.CustomerPortalBindingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "jdbc")
public class JdbcCustomerPortalBindingRepository implements CustomerPortalBindingRepository {

    private final DataSource dataSource;

    public JdbcCustomerPortalBindingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CustomerPortalBinding> findByUsername(String username) {
        String sql = """
                select username, customer_id, enabled, bound_by, bound_at
                  from auth_customer_portal_binding
                 where username = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取客户绑定失败", ex);
        }
    }

    @Override
    public Optional<CustomerPortalBinding> findByCustomerId(long customerId) {
        String sql = """
                select username, customer_id, enabled, bound_by, bound_at
                  from auth_customer_portal_binding
                 where customer_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取客户绑定失败", ex);
        }
    }

    /** 在调用方的事务连接上写入绑定。约束冲突由调用方回滚。 */
    void upsertOn(Connection connection, CustomerPortalBinding binding) throws SQLException {
        if (exists(connection, binding.username())) {
            updateOn(connection, binding);
            return;
        }
        insertOn(connection, binding);
    }

    private boolean exists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select username from auth_customer_portal_binding where username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void insertOn(Connection connection, CustomerPortalBinding binding) throws SQLException {
        String sql = """
                insert into auth_customer_portal_binding (
                    username, customer_id, enabled, bound_by, bound_at
                ) values (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, binding);
            statement.executeUpdate();
        }
    }

    private void updateOn(Connection connection, CustomerPortalBinding binding) throws SQLException {
        String sql = """
                update auth_customer_portal_binding
                   set customer_id = ?, enabled = ?, bound_by = ?, bound_at = ?
                 where username = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, binding.customerId());
            statement.setBoolean(2, binding.enabled());
            statement.setString(3, binding.boundBy());
            statement.setString(4, binding.boundAt());
            statement.setString(5, binding.username());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("客户绑定不存在：" + binding.username());
            }
        }
    }

    private static void bind(PreparedStatement statement, CustomerPortalBinding binding) throws SQLException {
        statement.setString(1, binding.username());
        statement.setLong(2, binding.customerId());
        statement.setBoolean(3, binding.enabled());
        statement.setString(4, binding.boundBy());
        statement.setString(5, binding.boundAt());
    }

    private static CustomerPortalBinding read(ResultSet result) throws SQLException {
        return new CustomerPortalBinding(
                result.getString("username"),
                result.getLong("customer_id"),
                result.getBoolean("enabled"),
                result.getString("bound_by"),
                result.getString("bound_at")
        );
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
