package com.zhubao.erp.hardware.domain;

public record HardwareAdapterPlan(
        String deviceCode,
        String deviceName,
        String phase,
        String primaryAdapter,
        String fallbackAdapter,
        String evidenceRequirement,
        String nextIntegrationAction,
        String riskLevel,
        String tone
) {
}
