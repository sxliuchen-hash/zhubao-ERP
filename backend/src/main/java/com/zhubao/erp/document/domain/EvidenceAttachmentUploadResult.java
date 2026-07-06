package com.zhubao.erp.document.domain;

public record EvidenceAttachmentUploadResult(
        String code,
        String documentCode,
        String fileName,
        String mediaType,
        long size,
        String storageLocation,
        String checksum,
        String uploadedAt,
        String previewUrl
) {
}
