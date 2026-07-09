package com.zhubao.erp.gateway.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Human command to consume a device reading into a business intent. Guarded by RBAC and the
 * operator context is resolved from the request (not carried in the body).
 */
public record ConsumeReadingCommand(
        @NotBlank String readingCode,
        @NotBlank String intent,
        String businessRefType,
        String businessRefCode,
        String note
) {
}
