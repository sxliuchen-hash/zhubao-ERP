package com.zhubao.erp.security.infra;

import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.CustomerAuthorizationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(name = "zhubao.security.customer-authorization.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerAuthorizationRepository implements CustomerAuthorizationRepository {

    // key = username|customerCode
    private final ConcurrentMap<String, CustomerAuthorization> grants = new ConcurrentHashMap<>();

    @Override
    public CustomerAuthorization grant(CustomerAuthorization authorization) {
        grants.put(key(authorization.username(), authorization.customerCode()), authorization);
        return authorization;
    }

    @Override
    public boolean revoke(String username, String customerCode) {
        return grants.remove(key(username, customerCode)) != null;
    }

    @Override
    public List<CustomerAuthorization> findByUsername(String username) {
        return grants.values().stream()
                .filter(grant -> grant.username().equals(username))
                .sorted(Comparator.comparing(CustomerAuthorization::customerCode))
                .toList();
    }

    @Override
    public List<CustomerAuthorization> findAll() {
        return grants.values().stream()
                .sorted(Comparator.comparing(CustomerAuthorization::username)
                        .thenComparing(CustomerAuthorization::customerCode))
                .toList();
    }

    Map<String, CustomerAuthorization> snapshot() {
        return Map.copyOf(grants);
    }

    private String key(String username, String customerCode) {
        return username + "|" + customerCode;
    }
}
