package com.zhubao.erp.security.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.CustomerPortalBindingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.auth.account-action.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerPortalBindingRepository implements CustomerPortalBindingRepository {

    private final Map<String, CustomerPortalBinding> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<CustomerPortalBinding> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(normalize(username)));
    }

    @Override
    public Optional<CustomerPortalBinding> findByCustomerId(long customerId) {
        return byUsername.values().stream()
                .filter(binding -> binding.customerId() == customerId)
                .findFirst();
    }

    /** 只供同一包内的身份动作调用。同一客户已有其他账号时拒绝。 */
    void save(CustomerPortalBinding binding) {
        for (CustomerPortalBinding current : byUsername.values()) {
            if (current.customerId() == binding.customerId() && !current.username().equals(binding.username())) {
                throw new BusinessException("本版一个客户只绑定一个账号");
            }
        }
        byUsername.put(binding.username(), binding);
    }

    /** 动作失败时撤掉刚写入的绑定。 */
    void remove(String username) {
        byUsername.remove(normalize(username));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
