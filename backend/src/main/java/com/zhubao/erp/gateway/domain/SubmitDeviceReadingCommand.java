package com.zhubao.erp.gateway.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Ingestion command posted by a low-cost hardware device / bridge.
 *
 * <p>Only {@code deviceCode}, {@code deviceKey}, {@code readingType} and {@code rawPayload} are
 * mandatory. Everything else is optional context that helps link the reading to a business
 * object (work order, mother order, weighing handover, etc.).</p>
 */
public record SubmitDeviceReadingCommand(
        @NotBlank String deviceCode,
        @NotBlank String deviceKey,
        @NotBlank String readingType,
        @NotBlank String rawPayload,
        String businessRefType,
        String businessRefCode,
        String capturedAt
) {
}
