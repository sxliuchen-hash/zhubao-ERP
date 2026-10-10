package com.zhubao.erp.document.domain;

/**
 * 已登记的存储内容。sizeBytes 为 -1 表示对象还不在本地磁盘上。
 */
public record StoredContentItem(
        String code,
        String fileName,
        String businessType,
        String businessCode,
        String owner,
        String storageLocation,
        String mediaType,
        String status,
        String uploadedAt,
        long sizeBytes,
        String sizeLabel
) {
}
