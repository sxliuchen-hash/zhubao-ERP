package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** 账号、客户授权和审计使用同一连接提交，最后管理员保护在事务内执行。 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "jdbc")
public class JdbcAuthAccountActionRepository implements AuthAccountActionRepository {

    private final DataSource dataSource;
    private final JdbcAuthUserAccountRepository accountRepository;
    private final JdbcAuthAuditRepository auditRepository;
    private final AuthAccountActionProbe probe;
    private final JdbcCustomerAuthorizationRepository customerRepository;

    @Autowired
    public JdbcAuthAccountActionRepository(DataSource dataSource, JdbcAuthUserAccountRepository accounts,
            JdbcAuthAuditRepository audits, CustomerAuthorizationRepository customers) {
        this(dataSource, accounts, audits, customers, AuthAccountActionProbe.NONE);
    }
    public JdbcAuthAccountActionRepository(DataSource dataSource, JdbcAuthUserAccountRepository accounts,
            JdbcAuthAuditRepository audits, CustomerAuthorizationRepository customers, AuthAccountActionProbe probe) {
        this.dataSource = dataSource;
        this.accountRepository = accounts;
        this.auditRepository = audits;
        this.probe = probe == null ? AuthAccountActionProbe.NONE : probe;
        if (customers != null && !(customers instanceof JdbcCustomerAuthorizationRepository)) {
            throw new IllegalArgumentException("JDBC 账号动作必须使用 JDBC 客户授权仓储");
        }
        this.customerRepository = (JdbcCustomerAuthorizationRepository) customers;
    }

    @Override
    public void commit(AuthAccountMutation mutation) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                accountRepository.assertAdminRemains(connection, mutation);
                probe.beforeAccountWrite();
                accountRepository.apply(connection, mutation);
                if (mutation.customerAuthorizations() != null && customerRepository != null) {
                    customerRepository.replaceOn(connection, mutation.username(), mutation.customerAuthorizations());
                }
                probe.beforeAuditWrite();
                auditRepository.insertOn(connection, mutation.audit());
                connection.commit();
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("账号动作事务失败", ex);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("账号动作事务失败", ex);
        }
    }

}
