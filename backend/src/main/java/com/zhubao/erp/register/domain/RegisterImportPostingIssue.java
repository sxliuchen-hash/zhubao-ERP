package com.zhubao.erp.register.domain;

public record RegisterImportPostingIssue(
        String field,
        String level,
        String message,
        String fallback
) {
}
