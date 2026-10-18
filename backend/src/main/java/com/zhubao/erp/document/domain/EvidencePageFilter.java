package com.zhubao.erp.document.domain;

public record EvidencePageFilter(
        String uploadedFrom,
        String uploadedTo,
        String mediaCategory,
        String ownerKeyword
) {
    public static EvidencePageFilter empty() {
        return new EvidencePageFilter("", "", "", "");
    }
}
