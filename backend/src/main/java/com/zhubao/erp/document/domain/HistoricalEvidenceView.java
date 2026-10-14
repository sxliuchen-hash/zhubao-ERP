package com.zhubao.erp.document.domain;

/**
 * 没有可靠操作引用的附件。采集场景能确认时允许显式补关联，不补造操作编号。
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
        String sceneCode,
        String previewUrl,
        String fileState
) {
}
