package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.CustomerPortalIdentityAction;
import com.zhubao.erp.security.domain.CustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.domain.AuthAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存中的客户身份事务。账号、绑定和审计在同一把锁内提交，任一步失败都恢复进入前的状态。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerPortalIdentityActionRepository implements CustomerPortalIdentityActionRepository {

    private final Object lock = new Object();
    private final AuthUserAccountRepository accounts;
    private final InMemoryCustomerPortalBindingRepository bindings;
    private final AuthAuditRepository audits;

    public InMemoryCustomerPortalIdentityActionRepository(AuthUserAccountRepository accounts,
            InMemoryCustomerPortalBindingRepository bindings, AuthAuditRepository audits) {
        this.accounts = accounts;
        this.bindings = bindings;
        this.audits = audits;
    }

    @Override
    public void commit(CustomerPortalIdentityAction action) {
        synchronized (lock) {
            String username = action.account().username();
            AuthUserAccount previousAccount = accounts.findByUsername(username).orElse(null);
            CustomerPortalBinding previousBinding = bindings.findByUsername(username).orElse(null);
            boolean accountWritten = false;
            boolean bindingWritten = false;
            try {
                accounts.save(action.account());
                accountWritten = true;
                bindings.save(action.binding());
                bindingWritten = true;
                audits.save(action.audit());
            } catch (RuntimeException ex) {
                if (bindingWritten) {
                    if (previousBinding == null) {
                        bindings.remove(username);
                    } else {
                        bindings.save(previousBinding);
                    }
                }
                if (accountWritten) {
                    if (previousAccount == null) {
                        accounts.delete(username);
                    } else {
                        accounts.save(previousAccount);
                    }
                }
                throw ex;
            }
        }
    }
}
