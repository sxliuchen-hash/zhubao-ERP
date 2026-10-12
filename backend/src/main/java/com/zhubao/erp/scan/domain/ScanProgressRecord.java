package com.zhubao.erp.scan.domain;

/**
 * 过程记录的查询形状。阶段保存稳定代码，中文只在展示层映射。
 * 补充记录只说明已经完成的正式业务之后又补了一句，不把单据改回进行中。
 */
public record ScanProgressRecord(
        String code,
        String targetType,
        String targetCode,
        String motherOrderCode,
        String stage,
        String process,
        String clauseNo,
        String note,
        String operatorId,
        String operatorName,
        String operatorRole,
        boolean supplementary,
        String recordedAt,
        String requestId
) {
}
