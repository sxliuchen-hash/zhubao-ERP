package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.Optional;

/** 公司料领用和退回。查询不写库存。 */
public interface CompanyMaterialIssueRepository {

    void insert(CompanyMaterialIssue issue, WorkSession session);

    Optional<CompanyMaterialIssue> find(String issueId);

    CompanyMaterialIssue addReturned(String issueId, BigDecimal quantity, int expectedVersion, WorkSession session);

    /** 同一领用上确认耗用和退回。耗用、退回和留下必须等于尚未结清数量。 */
    CompanyMaterialIssue dispose(String issueId, BigDecimal consumed, BigDecimal returned, BigDecimal kept,
                                 int expectedVersion, WorkSession session);

    void insert(CompanyMaterialReturn returned, WorkSession session);

    Optional<CompanyMaterialReturn> findReturn(String returnId);

    void insert(CompanyMaterialConsumption consumption, WorkSession session);

    Optional<CompanyMaterialConsumption> findConsumption(String consumptionId);
}
