package com.zhubao.erp.document.infra;

import com.zhubao.erp.document.domain.CustomerEvidenceGrant;
import com.zhubao.erp.document.domain.CustomerEvidenceGrantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.documents.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerEvidenceGrantRepository implements CustomerEvidenceGrantRepository {

    private final ConcurrentHashMap<String, CustomerEvidenceGrant> grants = new ConcurrentHashMap<>();

    @Override
    public void save(CustomerEvidenceGrant grant) {
        grants.put(grant.grantId(), grant);
    }

    @Override
    public Optional<CustomerEvidenceGrant> findById(String grantId) {
        return Optional.ofNullable(grants.get(grantId == null ? "" : grantId.trim()));
    }

    @Override
    public Optional<CustomerEvidenceGrant> find(long customerId, String attachmentCode) {
        String code = attachmentCode == null ? "" : attachmentCode.trim();
        return grants.values().stream()
                .filter(grant -> grant.customerId() == customerId && grant.attachmentCode().equals(code))
                .findFirst();
    }

    @Override
    public List<CustomerEvidenceGrant> listByCustomer(long customerId) {
        return grants.values().stream().filter(grant -> grant.customerId() == customerId).toList();
    }
}
