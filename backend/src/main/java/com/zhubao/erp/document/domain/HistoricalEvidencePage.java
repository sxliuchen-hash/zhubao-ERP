package com.zhubao.erp.document.domain;

import java.util.List;

public record HistoricalEvidencePage(
        List<HistoricalEvidenceView> items,
        Long nextCursor,
        boolean hasMore
) {
    public HistoricalEvidencePage {
        items = List.copyOf(items);
    }
}
