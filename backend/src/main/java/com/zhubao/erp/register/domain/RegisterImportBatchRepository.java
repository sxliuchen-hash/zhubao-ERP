package com.zhubao.erp.register.domain;

import java.util.List;
import java.util.Optional;

public interface RegisterImportBatchRepository {

    RegisterImportBatch save(RegisterImportBatch batch);

    RegisterImportBatch updateRowFields(
            String importBatchNo,
            int sourceLineNo,
            java.util.Map<String, String> normalizedFields,
            String nextAction
    );

    RegisterImportBatch markRowsPosted(String importBatchNo, List<Integer> sourceLineNos, boolean batchPosted, String nextAction);

    Optional<RegisterImportBatch> findByBatchNo(String importBatchNo);

    List<RegisterImportBatch> findRecent(int limit);
}
