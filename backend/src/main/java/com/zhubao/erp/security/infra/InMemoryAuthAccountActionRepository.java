package com.zhubao.erp.security.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.AuthAccountActionProbe;
import com.zhubao.erp.security.domain.AuthAccountActionRepository;
import com.zhubao.erp.security.domain.AuthAccountMutation;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationRepository;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存中的账号动作事务。同一锁内检查申请、写账号、写审计并消费申请；任一步失败都恢复到进入前的状态。
 * 这只保证单个进程内的原子性。多实例要使用 JDBC 实现上的条件更新。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuthAccountActionRepository implements AuthAccountActionRepository {

    private final Object lock = new Object();
    private final AuthUserAccountRepository accountRepository;
    private final AuthAuditRepository auditRepository;
    private final AuthAccountPreApprovalApplicationRepository applicationRepository;
    private final AuthAccountActionProbe probe;

    @Autowired
    public InMemoryAuthAccountActionRepository(
            AuthUserAccountRepository accountRepository,
            AuthAuditRepository auditRepository,
            AuthAccountPreApprovalApplicationRepository applicationRepository
    ) {
        this(accountRepository, auditRepository, applicationRepository, AuthAccountActionProbe.NONE);
    }

    public InMemoryAuthAccountActionRepository(
            AuthUserAccountRepository accountRepository,
            AuthAuditRepository auditRepository,
            AuthAccountPreApprovalApplicationRepository applicationRepository,
            AuthAccountActionProbe probe
    ) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.applicationRepository = applicationRepository;
        this.probe = probe == null ? AuthAccountActionProbe.NONE : probe;
    }

    @Override
    public void commit(AuthAccountMutation mutation) {
        synchronized (lock) {
            AuthAccountPreApprovalApplication currentApplication = lockApplication(mutation);
            AuthUserAccount previousAccount = accountRepository.findByUsername(mutation.username()).orElse(null);
            boolean accountWritten = false;
            String auditEventId = null;
            try {
                probe.beforeAccountWrite();
                applyAccount(mutation);
                accountWritten = true;
                probe.beforeAuditWrite();
                AuthAuditEvent savedAudit = auditRepository.save(mutation.audit());
                auditEventId = savedAudit.eventId();
                if (currentApplication != null) {
                    probe.beforeApplicationWrite();
                    applicationRepository.save(currentApplication.executed());
                }
            } catch (RuntimeException ex) {
                restore(mutation.username(), previousAccount, accountWritten, auditEventId, currentApplication);
                throw ex;
            }
        }
    }

    private AuthAccountPreApprovalApplication lockApplication(AuthAccountMutation mutation) {
        if (mutation.application() == null) {
            return null;
        }
        if (applicationRepository == null) {
            throw new BusinessException("账号前置审批申请单仓储未配置");
        }
        AuthAccountPreApprovalApplication current = applicationRepository.findByCode(mutation.application().applicationCode())
                .orElseThrow(() -> new BusinessException("账号前置审批申请单不存在："
                        + mutation.application().applicationCode()));
        if (!current.executable()) {
            throw new BusinessException("账号前置审批申请单尚未具备账号动作放行条件：" + current.applicationCode());
        }
        return current;
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
        }
    }

    private void restore(
            String username,
            AuthUserAccount previousAccount,
            boolean accountWritten,
            String auditEventId,
            AuthAccountPreApprovalApplication previousApplication
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
        if (previousApplication != null) {
            applicationRepository.save(previousApplication);
        }
    }
}
