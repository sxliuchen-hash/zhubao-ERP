package com.zhubao.erp.document.domain;

/**
 * 按操作展示的一条证据。多个附件各自成行，不拼成逗号字符串。
 */
public record EvidenceLinkView(
        String linkCode,
        String attachmentCode,
        String targetType,
        String targetCode,
        String stage,
        String scene,
        String sceneLabel,
        String operationType,
        String operationCode,
        String processName,
        String clauseNo,
        String operatorName,
        String linkedAt,
        String fileName,
        String mediaType,
        String checksum,
        String status,
        String previewUrl,
        String fileState
) {
}
