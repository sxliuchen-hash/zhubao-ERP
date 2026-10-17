package com.zhubao.erp.document.domain;

/**
 * 附件原件与元数据对账发现的一条异常。
 */
public record AttachmentIntegrityIssue(
        String category,
        String attachmentCode,
        String storageKey,
        String detail
) {
}
