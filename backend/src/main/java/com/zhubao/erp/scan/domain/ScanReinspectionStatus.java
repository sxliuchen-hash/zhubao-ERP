package com.zhubao.erp.scan.domain;

/**
 * 复检轮次尚未实施时的真实受限状态。available 为 false 不表示原不合格已经解决。
 */
public record ScanReinspectionStatus(
        boolean available,
        String reason
) {
}
