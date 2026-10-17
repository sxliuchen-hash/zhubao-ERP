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
        String tone,
        String objectType
) {
    public EvidenceAttachment withStatus(String nextStatus, String nextTone) {
        return new EvidenceAttachment(code, documentCode, businessType, businessCode, fileName,
                mediaType, storageLocation, storagePolicy, source, nextStatus, owner,
                checksum, uploadedAt, fallbackAction, nextTone, objectType);
    }
}
