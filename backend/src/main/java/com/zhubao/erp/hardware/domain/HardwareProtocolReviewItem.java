package com.zhubao.erp.hardware.domain;

import java.util.List;

public record HardwareProtocolReviewItem(
        String reviewCode,
        String deviceCode,
        String deviceName,
        String protocolType,
        String reviewStatus,
        String deviceModelStatus,
        List<String> confirmedParameters,
        List<String> pendingParameters,
        String evidenceCode,
        String owner,
        String reviewWindow,
        String blocker,
        String nextAction,
        String riskLevel,
        String tone
) {
}
