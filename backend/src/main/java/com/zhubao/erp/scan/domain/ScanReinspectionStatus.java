package com.zhubao.erp.scan.domain;

/**
 * 复检是否已经形成可放行结论。available 为 false 不表示原不合格已经解决。
 */
public record ScanReinspectionStatus(
        boolean available,
        String reason
) {
}
