package com.zhubao.erp.document.domain;

import java.util.List;
import java.util.Optional;

public interface CustomerEvidenceGrantRepository {

    void save(CustomerEvidenceGrant grant);

    Optional<CustomerEvidenceGrant> findById(String grantId);

    Optional<CustomerEvidenceGrant> find(long customerId, String attachmentCode);

    List<CustomerEvidenceGrant> listByCustomer(long customerId);
}
