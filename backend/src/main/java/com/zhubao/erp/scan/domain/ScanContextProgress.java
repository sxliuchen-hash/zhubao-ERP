package com.zhubao.erp.scan.domain;

import java.util.List;

/** 正式事实与过程记录分开。过程记录只返回已经保存的说明。 */
public record ScanContextProgress(
        List<ScanHandoffFact> handoffs,
        ScanQualityFact quality,
        ScanReinspectionStatus reinspection,
        List<ScanStockFact> stock,
        List<ScanShipmentFact> shipments,
        List<ScanProgressRecord> progressRecords
) {
    public ScanContextProgress {
        handoffs = handoffs == null ? List.of() : List.copyOf(handoffs);
        stock = stock == null ? List.of() : List.copyOf(stock);
        shipments = shipments == null ? List.of() : List.copyOf(shipments);
        progressRecords = progressRecords == null ? List.of() : List.copyOf(progressRecords);
    }
}
