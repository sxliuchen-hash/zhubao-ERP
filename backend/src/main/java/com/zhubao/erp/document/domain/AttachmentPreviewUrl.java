package com.zhubao.erp.document.domain;

public record AttachmentPreviewUrl(
        String code,
        String fileName,
        String mediaType,
        String url,
        String expiresAt
) {
}
