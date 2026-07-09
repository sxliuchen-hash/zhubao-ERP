package com.zhubao.erp.gateway.domain;

import java.util.List;

/**
 * A registered low-cost hardware endpoint allowed to push readings into the gateway.
 *
 * <p>Devices authenticate with a shared {@code deviceKey} rather than a human login, so the
 * ingestion path is decoupled from RBAC. The stored value is a salted hash of the key; the
 * raw key is only known to the physical device / bridge configuration.</p>
 */
public record GatewayDevice(
        String deviceCode,
        String deviceName,
        List<DeviceReadingType> acceptedTypes,
        String deviceKeyHash,
        boolean enabled,
        String location,
        String fallbackNote,
        String createdAt
) {
    public GatewayDevice {
        acceptedTypes = acceptedTypes == null ? List.of() : List.copyOf(acceptedTypes);
    }

    public boolean accepts(DeviceReadingType type) {
        return acceptedTypes.isEmpty() || acceptedTypes.contains(type);
    }
}
