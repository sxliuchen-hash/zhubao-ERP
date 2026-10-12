package com.zhubao.erp.document.domain;

/**
 * 没有可靠工序引用的旧附件。只读回看，不补造工序、条款或操作编号。
 */
public record HistoricalEvidenceView(
        String attachmentCode,
        String businessCode,
        String objectType,
        String fileName,
        String mediaType,
        String checksum,
        String status,
        String uploadedAt,
        String label,
        String previewUrl,
        String fileState
) {
}
