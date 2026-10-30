package com.zhubao.erp.document.domain;

import java.util.List;

/**
 * 客户可见的证据字段。不包含存放位置、工资或成本。
 */
public record CustomerEvidenceDetail(
        long customerId,
        String visibility,
        List<Item> items
) {
    public CustomerEvidenceDetail {
        items = items == null ? List.of() : List.copyOf(items);
        visibility = visibility == null ? "" : visibility;
    }

    public record Item(
            String grantId,
            String attachmentCode,
            String fileName,
            String mediaType,
            String kind,
            String downloadTicket
    ) {
    }
}
