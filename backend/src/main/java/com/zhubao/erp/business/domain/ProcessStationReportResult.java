package com.zhubao.erp.business.domain;

/**
 * 工序报工结果。中间工序留在在制，最后一道才转入半成品。
 */
public record ProcessStationReportResult(
        String orderCode,
        String processName,
        String ledgerCode,
        boolean movedToSemiFinished,
        String stockStatus
) {
}
