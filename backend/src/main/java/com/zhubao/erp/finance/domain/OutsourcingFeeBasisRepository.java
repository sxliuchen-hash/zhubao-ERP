package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/** 外协费用依据。查询不写应付、应收或工资。 */
public interface OutsourcingFeeBasisRepository {

    void save(OutsourcingFeeBasis basis, WorkSession session);

    Optional<OutsourcingFeeBasis> findByCompletion(String completionId);
}
