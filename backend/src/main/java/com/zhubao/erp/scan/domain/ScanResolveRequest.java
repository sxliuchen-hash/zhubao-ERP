package com.zhubao.erp.scan.domain;

import jakarta.validation.constraints.NotBlank;

public record ScanResolveRequest(
        @NotBlank String content,
        String scene
) {
}
