package com.zhubao.erp.document.domain;

import java.util.List;

public record EvidenceLinkPage(
        List<EvidenceLinkView> items,
        Long nextCursor,
        boolean hasMore
) {
    public EvidenceLinkPage {
        items = List.copyOf(items);
    }
}
