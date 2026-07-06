package com.zhubao.erp.document.domain;

public interface AttachmentObjectStorage {

    StoredAttachmentObject put(
            String documentCode,
            String attachmentCode,
            String fileName,
            String mediaType,
            byte[] content
    );

    byte[] get(String storageKey);
}
