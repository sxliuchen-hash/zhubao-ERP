package com.zhubao.erp.register.domain;

import java.util.List;
import java.util.Map;

public record RegisterImportPostingDraft(
        String importBatchNo,
        int sourceLineNo,
        String sourceRegisterSequence,
        String targetCommand,
        String status,
        Map<String, String> mappedFields,
        List<RegisterImportPostingIssue> missingFields,
        List<RegisterImportPreviewMessage> warningMessages
) {
}
