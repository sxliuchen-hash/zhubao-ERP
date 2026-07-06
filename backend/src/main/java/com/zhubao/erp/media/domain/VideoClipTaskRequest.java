package com.zhubao.erp.media.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record VideoClipTaskRequest(
        @NotBlank String bizType,
        @NotBlank String bizCode,
        @NotBlank String cameraCode,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        Integer bufferSeconds
) {
}
