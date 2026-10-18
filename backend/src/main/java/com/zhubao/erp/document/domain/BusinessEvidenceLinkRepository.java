package com.zhubao.erp.document.domain;

import java.util.List;
import java.util.Optional;

public interface BusinessEvidenceLinkRepository {

    List<BusinessEvidenceLink> findAll();

    default List<BusinessEvidenceLink> findByTarget(String targetType, String targetCode) {
        if (targetType == null || targetCode == null) {
            return List.of();
        }
        return findAll().stream()
                .filter(link -> targetType.equals(link.targetType()) && targetCode.equals(link.targetCode()))
                .toList();
    }

    Optional<BusinessEvidenceLink> findByKeyHash(String linkKeyHash);

    void insert(BusinessEvidenceLink link);
}
