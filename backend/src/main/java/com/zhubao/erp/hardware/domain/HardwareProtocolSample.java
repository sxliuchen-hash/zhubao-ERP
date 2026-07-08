package com.zhubao.erp.hardware.domain;

import java.util.List;

public record HardwareProtocolSample(
        String deviceCode,
        String deviceName,
        String protocolType,
        String sampleStatus,
        List<String> requiredParameters,
        String samplePayload,
        String evidenceCode,
        String captureMethod,
        String nextReviewAction,
        String riskLevel,
        String tone
) {
}
