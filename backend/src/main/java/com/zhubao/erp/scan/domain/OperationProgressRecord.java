package com.zhubao.erp.scan.domain;

/**
 * 追加的现场过程说明。不代表工序交付、质检结论、工资或出货。
 */
public record OperationProgressRecord(
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
        String requestId,
        String contentHash
) {
    public ScanProgressRecord toScan() {
        return new ScanProgressRecord(
                code,
                targetType,
                targetCode,
                motherOrderCode,
                stage,
                blankToNull(process),
                blankToNull(clauseNo),
                note,
                operatorId,
                operatorName,
                operatorRole,
                supplementary,
                recordedAt,
                requestId
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
