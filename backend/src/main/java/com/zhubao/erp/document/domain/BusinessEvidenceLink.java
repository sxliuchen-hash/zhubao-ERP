package com.zhubao.erp.document.domain;

/**
 * 一条业务证据引用。文件仍在附件表，场景和操作记在引用上，互不覆盖。
 */
public record BusinessEvidenceLink(
        String linkCode,
        String attachmentCode,
        String targetType,
        String targetCode,
        String stage,
        String scene,
        String operationType,
        String operationCode,
        String processName,
        String clauseNo,
        String operatorName,
        String linkedAt,
        String linkKeyHash
) {
}
