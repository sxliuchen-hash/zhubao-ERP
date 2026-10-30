package com.zhubao.erp.document.domain;

/**
 * 管理员明确获准后，客户才能看到的一张附件。没有获准记录就不展示，不代表可见范围已经核定。
 */
public record CustomerEvidenceGrant(
        String grantId,
        long customerId,
        String attachmentCode,
        String kind
) {
    public CustomerEvidenceGrant {
        grantId = grantId == null ? "" : grantId.trim();
        attachmentCode = attachmentCode == null ? "" : attachmentCode.trim();
        kind = kind == null ? "" : kind.trim();
        if (grantId.isEmpty() || customerId <= 0 || attachmentCode.isEmpty() || kind.isEmpty()) {
            throw new IllegalArgumentException("客户证据获准缺少编号、客户或附件");
        }
    }
}
