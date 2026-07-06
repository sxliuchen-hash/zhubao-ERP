package com.zhubao.erp.document.domain;

public record DocumentPackage(
        String code,
        String title,
        String category,
        String businessCode,
        String owner,
        String purpose,
        int requiredEvidence,
        int linkedEvidence,
        String status,
        String retentionPolicy,
        String lastUpdated,
        String tone
) {
}
