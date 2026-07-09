package com.zhubao.erp.gateway.domain;

/**
 * The business intent a human operator assigns to a device reading when consuming it.
 *
 * <p>Consumption is an accountable human action: it records who turned a raw device reading into a
 * business signal and for what purpose. It intentionally does not itself write the business ledger
 * (that stays with the existing {@code BusinessOperationService} commands); it produces an audited
 * bridge record so telemetry and business actions can be reconciled.</p>
 */
public enum ReadingConsumptionIntent {

    /** Use a scale reading as the confirmed weight for a weighing handover. */
    WEIGH_HANDOVER("称重交接", DeviceReadingType.SCALE_WEIGHT),

    /** Use a scan reading to locate/report a work order. */
    SCAN_TO_REPORT("扫码报工", DeviceReadingType.SCAN_EVENT),

    /** Confirm a label print acknowledgement. */
    PRINT_CONFIRM("标签打印确认", DeviceReadingType.PRINT_ACK),

    /** Link a video clip mark to a business object. */
    VIDEO_LINK("视频证据关联", DeviceReadingType.VIDEO_CLIP_MARK),

    /** Explicitly dismiss a reading (noise, duplicate, test). */
    DISMISS("忽略读数", null);

    private final String label;
    private final DeviceReadingType requiredType;

    ReadingConsumptionIntent(String label, DeviceReadingType requiredType) {
        this.label = label;
        this.requiredType = requiredType;
    }

    public String label() {
        return label;
    }

    /** The reading type this intent applies to, or {@code null} if any type is acceptable. */
    public DeviceReadingType requiredType() {
        return requiredType;
    }

    public static ReadingConsumptionIntent fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ReadingConsumptionIntent intent : values()) {
            if (intent.name().equalsIgnoreCase(value.trim())) {
                return intent;
            }
        }
        return null;
    }
}
