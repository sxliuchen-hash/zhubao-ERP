package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "zhubao.security.customer-authorization.repository", havingValue = "jdbc")
public class JdbcCustomerAuthorizationRepository implements CustomerAuthorizationRepository {

    private final DataSource dataSource;

    public JdbcCustomerAuthorizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CustomerAuthorization grant(CustomerAuthorization authorization) {
        String sql = """
                insert into sys_customer_authorization (username, customer_code, customer_name, granted_by, granted_at)
                values (?, ?, ?, ?, ?)
                on duplicate key update customer_name = values(customer_name),
                                        granted_by = values(granted_by),
                                        granted_at = values(granted_at)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, authorization.username());
            statement.setString(2, authorization.customerCode());
            statement.setString(3, authorization.customerName());
            statement.setString(4, authorization.grantedBy());
            statement.setString(5, authorization.grantedAt());
            statement.executeUpdate();
            return authorization;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to grant customer authorization: "
                    + authorization.username() + "/" + authorization.customerCode(), ex);
        }
    }

    @Override
    public boolean revoke(String username, String customerCode) {
        String sql = "delete from sys_customer_authorization where username = ? and customer_code = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, customerCode);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to revoke customer authorization: "
                    + username + "/" + customerCode, ex);
        }
    }

    @Override
    public List<CustomerAuthorization> findByUsername(String username) {
        String sql = selectSql() + " where username = ? order by customer_code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load customer authorizations for: " + username, ex);
        }
    }

    @Override
    public List<CustomerAuthorization> findAll() {
        String sql = selectSql() + " order by username, customer_code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return list(statement);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load customer authorizations", ex);
        }
    }

    private List<CustomerAuthorization> list(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<CustomerAuthorization> grants = new ArrayList<>();
            while (rs.next()) {
                grants.add(new CustomerAuthorization(
                        rs.getString("username"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getString("granted_by"),
                        rs.getString("granted_at")
                ));
            }
            return List.copyOf(grants);
        }
    }

    private String selectSql() {
        return "select username, customer_code, customer_name, granted_by, granted_at from sys_customer_authorization";
    }
}
