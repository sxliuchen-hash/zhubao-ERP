package com.zhubao.erp.register.domain;

import java.util.List;

public record RegisterImportPostingExecutionResult(
        String importBatchNo,
        String templateCode,
        String templateName,
        String status,
        int totalDrafts,
        int postedDrafts,
        int skippedDrafts,
        int blockedDrafts,
        int warningDrafts,
        String operator,
        String postedAt,
        String nextAction,
        List<RegisterImportPostingExecutionItem> items
) {
}
