package com.zhubao.erp.document.domain;

/**
 * 获准详情签发的下载结果。只回显文件名和类型，不回显存放位置。
 */
public record CustomerEvidenceDownload(
        String grantId,
        String attachmentCode,
        String fileName,
        String mediaType,
        String kind
) {
}
