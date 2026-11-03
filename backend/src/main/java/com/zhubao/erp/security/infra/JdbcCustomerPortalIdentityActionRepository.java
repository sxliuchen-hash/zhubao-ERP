package com.zhubao.erp.security.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.CustomerPortalIdentityAction;
import com.zhubao.erp.security.domain.CustomerPortalIdentityActionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 客户身份的 JDBC 事务。账号、绑定和审计使用同一连接，失败全部回滚。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "jdbc")
public class JdbcCustomerPortalIdentityActionRepository implements CustomerPortalIdentityActionRepository {

    private final DataSource dataSource;
    private final JdbcAuthUserAccountRepository accounts;
    private final JdbcCustomerPortalBindingRepository bindings;
    private final JdbcAuthAuditRepository audits;

    public JdbcCustomerPortalIdentityActionRepository(DataSource dataSource, JdbcAuthUserAccountRepository accounts,
            JdbcCustomerPortalBindingRepository bindings, JdbcAuthAuditRepository audits) {
        this.dataSource = dataSource;
        this.accounts = accounts;
        this.bindings = bindings;
        this.audits = audits;
    }

    @Override
    public void commit(CustomerPortalIdentityAction action) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                accounts.apply(connection, com.zhubao.erp.security.domain.AuthAccountMutation.save(
                        action.account(), action.audit()));
                bindings.upsertOn(connection, action.binding());
                audits.insertOn(connection, action.audit());
                connection.commit();
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                SQLException sql = (SQLException) ex;
                String message = sql.getMessage() == null ? "" : sql.getMessage();
                if (message.toLowerCase(Locale.ROOT).contains("uk_auth_customer_portal_customer")) {
                    throw new BusinessException("本版一个客户只绑定一个账号");
                }
                throw new IllegalStateException("客户身份事务失败", sql);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("客户身份事务失败", ex);
        }
    }
}
