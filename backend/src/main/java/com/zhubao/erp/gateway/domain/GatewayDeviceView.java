package com.zhubao.erp.gateway.domain;

import java.util.List;

/**
 * Read-only projection of a registered gateway device, never exposing the key hash.
 */
public record GatewayDeviceView(
        String deviceCode,
        String deviceName,
        List<DeviceReadingType> acceptedTypes,
        boolean enabled,
        String location,
        String fallbackNote,
        int recentReadings,
        String lastReadingAt
) {
    public GatewayDeviceView {
        acceptedTypes = acceptedTypes == null ? List.of() : List.copyOf(acceptedTypes);
    }
}
