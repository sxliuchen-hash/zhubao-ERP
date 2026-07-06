package com.zhubao.erp.document.domain;

public record StoredAttachmentObject(
        String storageKey,
        String storageLocation,
        String mediaType,
        long size,
        String checksum
) {
}
