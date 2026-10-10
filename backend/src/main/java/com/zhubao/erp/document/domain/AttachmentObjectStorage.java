package com.zhubao.erp.document.domain;

import java.io.InputStream;

public interface AttachmentObjectStorage {

    StoredAttachmentObject put(
            String documentCode,
            String attachmentCode,
            String fileName,
            String mediaType,
            byte[] content
    );

    /**
     * 按流写入本地存储，边写边计算校验和，超过 {@code maxBytes} 时删除已写文件并拒绝。
     */
    StoredAttachmentObject putStream(
            String documentCode,
            String attachmentCode,
            String fileName,
            String mediaType,
            InputStream input,
            long declaredSize,
            long maxBytes
    );

    byte[] get(String storageKey);

    void delete(String storageKey);
}
