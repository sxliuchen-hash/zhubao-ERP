package com.zhubao.erp.hardware.domain;

public record HardwareDevice(
        String code,
        String name,
        String scene,
        String protocol,
        HardwareDeviceStatus status,
        String fallback
) {
}
