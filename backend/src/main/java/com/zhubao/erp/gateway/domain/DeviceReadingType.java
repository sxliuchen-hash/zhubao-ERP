package com.zhubao.erp.gateway.domain;

/**
 * Reading types accepted by the low-cost hardware ingestion gateway.
 *
 * <p>The gateway intentionally avoids vendor SDKs. Any device capable of sending an HTTP request
 * (serial-to-HTTP bridge, ESP32, browser bridge, PC keypad relay) can submit one of these
 * standardized reading types, and {@code DeviceReadingNormalizer} turns the raw payload into a
 * structured value.</p>
 */
public enum DeviceReadingType {

    /** Electronic scale weight, e.g. {@code ST,GS,+000123.45,g} or {@code 123.45g}. */
    SCALE_WEIGHT("电子秤称重"),

    /** Barcode / QR scan event, e.g. {@code WO|ZD-20260706-001|PROC=POLISH}. */
    SCAN_EVENT("扫码事件"),

    /** Label printer acknowledgement, e.g. {@code OK|PDF-LABEL-MD-20260706-001}. */
    PRINT_ACK("标签打印回执"),

    /** Video clip start/end marker for NVR half-automatic clipping. */
    VIDEO_CLIP_MARK("视频截取标记"),

    /** Device liveness heartbeat. */
    HEARTBEAT("设备心跳");

    private final String label;

    DeviceReadingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DeviceReadingType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeviceReadingType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
