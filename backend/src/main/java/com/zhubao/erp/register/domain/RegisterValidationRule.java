package com.zhubao.erp.register.domain;

public record RegisterValidationRule(
        String code,
        String level,
        String description,
        String fallback
) {
}
