package com.zhubao.erp.register.domain;

import java.util.List;
import java.util.Map;

public record RegisterImportBatchRow(
        String importBatchNo,
        int sourceLineNo,
        String sourceRegisterSequence,
        String status,
        Map<String, String> normalizedFields,
        List<RegisterImportPreviewMessage> messages
) {
}
