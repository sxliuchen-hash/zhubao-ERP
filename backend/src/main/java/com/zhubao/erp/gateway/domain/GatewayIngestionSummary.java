package com.zhubao.erp.gateway.domain;

public record GatewayIngestionSummary(
        int totalDevices,
        int enabledDevices,
        int recentReadings,
        long readyReadings,
        long warningReadings,
        long blockedReadings
) {
}
