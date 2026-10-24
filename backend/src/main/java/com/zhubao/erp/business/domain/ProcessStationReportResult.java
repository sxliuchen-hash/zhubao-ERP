package com.zhubao.erp.business.domain;

/**
 * 工序报工结果。中间工序留在在制，最后一道才转入半成品。
 */
public record ProcessStationReportResult(
        String orderCode,
        String processName,
        String ledgerCode,
        boolean movedToSemiFinished,
        String stockStatus,
        String batchCode,
        java.math.BigDecimal batchQuantity,
        java.math.BigDecimal deliveredQuantity,
        java.math.BigDecimal remainingQuantity,
        Boolean processCompleted,
        java.math.BigDecimal finalWeight
) {
    public ProcessStationReportResult(String orderCode, String processName, String ledgerCode,
                                     boolean movedToSemiFinished, String stockStatus) {
        this(orderCode, processName, ledgerCode, movedToSemiFinished, stockStatus, null, null, null, null, true, null);
    }
}
