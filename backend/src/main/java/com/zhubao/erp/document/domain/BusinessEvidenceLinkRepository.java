package com.zhubao.erp.document.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface BusinessEvidenceLinkRepository {

    List<BusinessEvidenceLink> findAll();

    default BusinessEvidenceLinkPage findPageByTarget(
            String targetType,
            String targetCode,
            long afterId,
            int limit
    ) {
        throw new UnsupportedOperationException("业务证据引用分页未实现");
    }

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

    /**
     * 在当前工作单元里保存引用。命令失败时不应留下这次引用。
     */
    default void insert(BusinessEvidenceLink link, WorkSession session) {
        insert(link);
    }
}
