package com.zhubao.erp.scan.domain;

import java.util.List;
import java.util.Optional;

/** 过程记录仓储。同一请求、同一操作者、同一目标和同一内容只保留一条。 */
public interface OperationProgressRepository {

    void insert(OperationProgressRecord record);

    Optional<OperationProgressRecord> findByCode(String code);

    List<OperationProgressRecord> findByRequestId(String requestId);

    List<OperationProgressRecord> findByTarget(String targetType, String targetCode);

    Optional<OperationProgressRecord> findReplay(
            String requestId,
            String operatorId,
            String targetType,
            String targetCode,
            String contentHash
    );
}
