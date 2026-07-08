package com.zhubao.erp.register.domain;

public record RegisterImportPostingExecutionItem(
        int sourceLineNo,
        String sourceRegisterSequence,
        String targetCommand,
        String status,
        String businessCode,
        String ledgerCode,
        String message
) {
}
