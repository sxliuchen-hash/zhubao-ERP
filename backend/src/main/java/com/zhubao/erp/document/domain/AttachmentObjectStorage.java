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

    long size(String storageKey);

    InputStream open(String storageKey);

    /** 从可恢复的原件生成指定清晰度图片，返回派生对象的存储键。 */
    default String ensureImageDerivative(String storageKey, String resolution) {
        throw new UnsupportedOperationException("当前附件存储不支持图片派生图");
    }

    /** 从视频原件重建封面，返回派生对象的存储键。 */
    default String ensureVideoPoster(String storageKey) {
        throw new UnsupportedOperationException("当前附件存储不支持视频封面");
    }

    void delete(String storageKey);
}
