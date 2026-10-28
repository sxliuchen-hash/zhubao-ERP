package com.zhubao.erp.security.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存中的账号动作事务。同一锁内保护最后管理员、写账号、客户授权和审计；任一步失败都恢复到进入前的状态。
 * 这只保证单个进程内的原子性。多实例要使用 JDBC 实现上的条件更新。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthAccountActionRepository implements AuthAccountActionRepository {

    private final Object lock = new Object();
    private final AuthUserAccountRepository accountRepository;
    private final AuthAuditRepository auditRepository;
    private final AuthAccountActionProbe probe;
    private final CustomerAuthorizationRepository customerRepository;

    @Autowired
    public InMemoryAuthAccountActionRepository(AuthUserAccountRepository accounts, AuthAuditRepository audits,
                                               CustomerAuthorizationRepository customers) {
        this(accounts, audits, customers, AuthAccountActionProbe.NONE);
    }
    public InMemoryAuthAccountActionRepository(AuthUserAccountRepository accounts, AuthAuditRepository audits,
                                               CustomerAuthorizationRepository customers, AuthAccountActionProbe probe) {
        this.accountRepository = accounts;
        this.auditRepository = audits;
        this.customerRepository = customers;
        this.probe = probe == null ? AuthAccountActionProbe.NONE : probe;
    }

    @Override
    public void commit(AuthAccountMutation mutation) {
        synchronized (lock) {
            AuthUserAccount previousAccount = accountRepository.findByUsername(mutation.username()).orElse(null);
            if (mutation.removesEnabledAdmin() && previousAccount != null && previousAccount.enabled()
                    && previousAccount.role() == RoleCode.ADMIN && accountRepository.findAll().stream()
                    .filter(account -> account.enabled() && account.role() == RoleCode.ADMIN).count() <= 1) {
                throw new BusinessException("必须保留至少一个启用的系统管理员");
            }
            java.util.List<CustomerAuthorization> previousAuthorizations = customerRepository == null ? null
                    : customerRepository.findByUsername(mutation.username());
            boolean accountWritten = false;
            String auditEventId = null;
            try {
                probe.beforeAccountWrite();
                applyAccount(mutation);
                accountWritten = true;
                if (mutation.customerAuthorizations() != null && customerRepository != null) {
                    customerRepository.replace(mutation.username(), mutation.customerAuthorizations());
                }
                probe.beforeAuditWrite();
                AuthAuditEvent savedAudit = auditRepository.save(mutation.audit());
                auditEventId = savedAudit.eventId();
            } catch (RuntimeException ex) {
                restore(mutation.username(), previousAccount, accountWritten, auditEventId);
                if (mutation.customerAuthorizations() != null && previousAuthorizations != null) {
                    customerRepository.replace(mutation.username(), previousAuthorizations);
                }
                throw ex;
            }
        }
    }

    private void applyAccount(AuthAccountMutation mutation) {
        switch (mutation.kind()) {
            case SAVE -> {
                if (mutation.account() == null) {
                    throw new BusinessException("保存账号缺少账号资料");
                }
                accountRepository.save(mutation.account());
            }
            case SET_ENABLED -> {
                if (accountRepository.findByUsername(mutation.username()).isEmpty()) {
                    throw new BusinessException("账号不存在：" + mutation.username());
                }
                accountRepository.updateEnabled(mutation.username(), Boolean.TRUE.equals(mutation.enabled()));
            }
            case UNLOCK -> {
                if (accountRepository.findByUsername(mutation.username()).isEmpty()) {
                    throw new BusinessException("账号不存在：" + mutation.username());
                }
                accountRepository.unlock(mutation.username());
            }
            case DELETE -> {
                if (accountRepository.findByUsername(mutation.username()).isEmpty()) {
                    throw new BusinessException("账号不存在：" + mutation.username());
                }
                accountRepository.delete(mutation.username());
            }
        }
    }

    private void restore(
            String username,
            AuthUserAccount previousAccount,
            boolean accountWritten,
            String auditEventId
    ) {
        if (auditEventId != null && auditRepository instanceof InMemoryAuthAuditRepository memoryAudit) {
            memoryAudit.discard(auditEventId);
        }
        if (accountWritten) {
            if (previousAccount == null && accountRepository instanceof InMemoryAuthUserAccountRepository memoryAccounts) {
                memoryAccounts.delete(username);
            } else if (previousAccount != null) {
                accountRepository.save(previousAccount);
            }
        }
    }
}
