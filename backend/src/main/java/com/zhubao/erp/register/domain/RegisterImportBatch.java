package com.zhubao.erp.register.domain;

import java.util.List;

public record RegisterImportBatch(
        String importBatchNo,
        String templateCode,
        String templateName,
        String sourceFileName,
        String targetCommand,
        int totalRows,
        int readyRows,
        int warningRows,
        int blockedRows,
        boolean executable,
        String status,
        String confirmedBy,
        String confirmedAt,
        String confirmRemark,
        String nextAction,
        List<RegisterImportBatchRow> rows
) {
}
