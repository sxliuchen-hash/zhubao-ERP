package com.zhubao.erp.register.domain;

public record RegisterImportPreviewMessage(
        String ruleCode,
        String level,
        String field,
        String message,
        String fallback
) {
}
