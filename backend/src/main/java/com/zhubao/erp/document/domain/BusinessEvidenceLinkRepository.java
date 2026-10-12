package com.zhubao.erp.document.domain;

import java.util.List;
import java.util.Optional;

public interface BusinessEvidenceLinkRepository {

    List<BusinessEvidenceLink> findAll();

    Optional<BusinessEvidenceLink> findByKeyHash(String linkKeyHash);

    void insert(BusinessEvidenceLink link);
}
