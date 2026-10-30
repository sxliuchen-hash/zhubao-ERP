package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.Optional;

/** 公司料领用和退回。查询不写库存。 */
public interface CompanyMaterialIssueRepository {

    void insert(CompanyMaterialIssue issue, WorkSession session);

    Optional<CompanyMaterialIssue> find(String issueId);

    CompanyMaterialIssue addReturned(String issueId, BigDecimal quantity, int expectedVersion, WorkSession session);

    void insert(CompanyMaterialReturn returned, WorkSession session);

    Optional<CompanyMaterialReturn> findReturn(String returnId);
}
