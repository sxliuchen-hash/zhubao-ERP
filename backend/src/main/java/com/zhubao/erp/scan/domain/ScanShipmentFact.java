package com.zhubao.erp.scan.domain;

/** 已经存在的出货事实。准备记录不在这里伪装成已发货。 */
public record ScanShipmentFact(
        String outboundCode,
        String motherOrderCode,
        String workOrderCode,
        String logisticsNo,
        String status,
        String ledgerCode
) {
}
