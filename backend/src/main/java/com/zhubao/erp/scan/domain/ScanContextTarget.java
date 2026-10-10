package com.zhubao.erp.scan.domain;

/**
 * 本次成功定位的规范身份。需要选择时各字段为空，不能默认第一张单。
 */
public record ScanContextTarget(
        ScanObjectType objectType,
        String code,
        String motherOrderCode,
        String workOrderCode,
        String reworkOrderCode,
        String outboundOrderCode,
        String reference
) {
}
