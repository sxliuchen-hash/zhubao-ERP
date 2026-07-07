package com.zhubao.erp.register.domain;

public record RegisterFieldMapping(
        String sourceColumn,
        String targetField,
        String targetObject,
        boolean required,
        String normalizeRule,
        String validationRule
) {
}
