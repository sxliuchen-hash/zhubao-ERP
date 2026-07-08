package com.zhubao.erp.hardware.domain;

import java.util.List;

public record HardwareProtocolExecutionResult(
        String executionCode,
        String reviewCode,
        String deviceCode,
        String deviceName,
        String protocolType,
        String executionStatus,
        String executedAt,
        String executedBy,
        String deviceModelSnapshot,
        String modelFreezeStatus,
        List<String> acceptedParameters,
        List<String> failedParameters,
        String evidenceCode,
        String evidenceStatus,
        String resultSummary,
        String nextAction,
        String riskLevel,
        String tone
) {
}
