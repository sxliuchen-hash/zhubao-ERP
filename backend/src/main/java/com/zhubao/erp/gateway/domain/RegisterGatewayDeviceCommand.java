package com.zhubao.erp.gateway.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Management command to register a new gateway device or update an existing one.
 *
 * <p>{@code deviceKey} is optional on update (blank keeps the current key hash); on create it is
 * required. Guarded by {@code GATEWAY_DEVICE_MANAGE}.</p>
 */
public record RegisterGatewayDeviceCommand(
        @NotBlank String deviceCode,
        @NotBlank String deviceName,
        List<String> acceptedTypes,
        String deviceKey,
        Boolean enabled,
        String location,
        String fallbackNote
) {
    public RegisterGatewayDeviceCommand {
        acceptedTypes = acceptedTypes == null ? List.of() : List.copyOf(acceptedTypes);
    }
}
