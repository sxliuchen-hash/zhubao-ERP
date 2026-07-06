package com.zhubao.erp.document.domain;

public record AttachmentContent(
        String code,
        String fileName,
        String mediaType,
        byte[] content
) {
}
