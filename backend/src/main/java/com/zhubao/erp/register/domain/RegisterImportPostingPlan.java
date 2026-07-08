package com.zhubao.erp.register.domain;

import java.util.List;

public record RegisterImportPostingPlan(
        String importBatchNo,
        String templateCode,
        String templateName,
        String targetCommand,
        String status,
        int totalRows,
        int readyDrafts,
        int warningDrafts,
        int blockedDrafts,
        String nextAction,
        List<RegisterImportPostingDraft> drafts
) {
}
