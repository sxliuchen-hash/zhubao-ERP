package com.zhubao.erp.document.domain;

import java.util.List;

/**
 * 只读附件对账结果，不自动清理或修改原件。
 */
public record AttachmentIntegrityReport(
        String checkedAt,
        int metadataCount,
        int originalCount,
        List<AttachmentIntegrityIssue> issues
) {
    public AttachmentIntegrityReport {
        issues = List.copyOf(issues);
    }
}
