package com.zhubao.erp.register.domain;

import java.util.List;

public record RegisterImportTemplate(
        String code,
        String name,
        String sourceFile,
        String businessFlow,
        String targetCommand,
        String targetLedgerType,
        String parserStatus,
        String nextAction,
        List<RegisterFieldMapping> fields,
        List<RegisterValidationRule> validationRules
) {
}
