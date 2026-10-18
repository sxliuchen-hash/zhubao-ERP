package com.zhubao.erp.document.domain;

import java.util.List;

public record BusinessEvidenceLinkPage(
        List<Entry> entries,
        boolean hasMore
) {
    public BusinessEvidenceLinkPage {
        entries = List.copyOf(entries);
    }

    public record Entry(long id, BusinessEvidenceLink link) {
    }
}
