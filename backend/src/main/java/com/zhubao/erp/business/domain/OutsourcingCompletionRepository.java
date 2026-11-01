package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/** 外协完成量。同一收回只保存一次。 */
public interface OutsourcingCompletionRepository {

    void save(OutsourcingCompletion completion, WorkSession session);

    Optional<OutsourcingCompletion> find(String completionId);

    Optional<OutsourcingCompletion> findByReturn(String returnId);

    java.util.List<OutsourcingCompletion> listByTarget(String targetCode);
}
