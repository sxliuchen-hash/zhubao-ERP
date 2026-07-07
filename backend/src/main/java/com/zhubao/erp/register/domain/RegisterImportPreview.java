package com.zhubao.erp.register.domain;

import java.util.List;

public record RegisterImportPreview(
        String importBatchNo,
        String templateCode,
        String templateName,
        String sourceFileName,
        int totalRows,
        int readyRows,
        int warningRows,
        int blockedRows,
        boolean executable,
        String nextAction,
        List<RegisterImportPreviewRow> rows
) {
}
