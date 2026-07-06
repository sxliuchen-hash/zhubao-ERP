package com.zhubao.erp.document.domain;

public record EvidenceAttachment(
        String code,
        String documentCode,
        String businessType,
        String businessCode,
        String fileName,
        String mediaType,
        String storageLocation,
        String storagePolicy,
        String source,
        String status,
        String owner,
        String checksum,
        String uploadedAt,
        String fallbackAction,
        String tone
) {
}
