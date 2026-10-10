package com.zhubao.erp.scan.domain;

/**
 * 过程记录的响应形状。保存动作在后续增量开放，当前查询只返回空集合。
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
        String recordedAt,
        String requestId
) {
}
