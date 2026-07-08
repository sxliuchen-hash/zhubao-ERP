package com.zhubao.erp.hardware.domain;

import java.util.List;

public record HardwareProtocolSignoffReadiness(
        String signoffCode,
        String executionCode,
        String deviceCode,
        String deviceName,
        String signoffStatus,
        List<String> requiredAttachments,
        List<String> missingAttachments,
        List<String> signerRoles,
        String signedBy,
        String signedAt,
        String modelFreezeDecision,
        String rolloutGate,
        String closureSummary,
        String nextAction,
        String riskLevel,
        String tone
) {
}
