package com.zhubao.erp.security.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC 账号动作事务。先用条件更新锁住仍可执行的申请行，再在同一连接里写账号和审计，最后把申请标为已执行。
 * 任一步失败都回滚，申请仍可重试。锁在数据库行上，不依赖单个 JVM 的 synchronized，因此多个应用实例同时执行时只有一个能提交。
 * 本类不把 H2 兼容模式的结果当成真实 MySQL 验证。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "jdbc")
public class JdbcAuthAccountActionRepository implements AuthAccountActionRepository {

    private final DataSource dataSource;
    private final JdbcAuthUserAccountRepository accountRepository;
    private final JdbcAuthAuditRepository auditRepository;
    private final JdbcAuthAccountPreApprovalApplicationRepository applicationRepository;
    private final AuthAccountActionProbe probe;

    @Autowired
    public JdbcAuthAccountActionRepository(
            DataSource dataSource,
            JdbcAuthUserAccountRepository accountRepository,
            JdbcAuthAuditRepository auditRepository,
            JdbcAuthAccountPreApprovalApplicationRepository applicationRepository
    ) {
        this(dataSource, accountRepository, auditRepository, applicationRepository, AuthAccountActionProbe.NONE);
    }

    public JdbcAuthAccountActionRepository(
            DataSource dataSource,
            JdbcAuthUserAccountRepository accountRepository,
            JdbcAuthAuditRepository auditRepository,
            JdbcAuthAccountPreApprovalApplicationRepository applicationRepository,
            AuthAccountActionProbe probe
    ) {
        this.dataSource = dataSource;
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.applicationRepository = applicationRepository;
        this.probe = probe == null ? AuthAccountActionProbe.NONE : probe;
    }

    @Override
    public void commit(AuthAccountMutation mutation) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String applicationCode = mutation.application() == null
                        ? null
                        : mutation.application().applicationCode();
                if (applicationCode != null && applicationRepository.lockReady(connection, applicationCode) != 1) {
                    throw notExecutable(applicationCode);
                }
                probe.beforeAccountWrite();
                accountRepository.apply(connection, mutation);
                probe.beforeAuditWrite();
                auditRepository.insertOn(connection, mutation.audit());
                if (applicationCode != null) {
                    probe.beforeApplicationWrite();
                    if (applicationRepository.markExecuted(connection, applicationCode) != 1) {
                        throw notExecutable(applicationCode);
                    }
                }
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

    private BusinessException notExecutable(String applicationCode) {
        return new BusinessException("账号前置审批申请单尚未具备账号动作放行条件：" + applicationCode);
    }
}
