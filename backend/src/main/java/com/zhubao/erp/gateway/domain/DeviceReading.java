package com.zhubao.erp.gateway.domain;

/**
 * A normalized reading ingested from a low-cost hardware device.
 *
 * <p>{@code rawPayload} keeps the untouched device output for audit and manual recovery, while
 * {@code normalizedValue} / {@code normalizedUnit} / {@code normalizedCode} carry the structured
 * result produced by {@code DeviceReadingNormalizer}. Downstream business actions (weighing
 * handover, scan-to-report, video-clip marks) consume the normalized fields.</p>
 */
public record DeviceReading(
        String readingCode,
        String deviceCode,
        DeviceReadingType readingType,
        String rawPayload,
        String normalizedValue,
        String normalizedUnit,
        String normalizedCode,
        DeviceReadingStatus status,
        String message,
        String businessRefType,
        String businessRefCode,
        String capturedAt,
        String receivedAt
) {
}
