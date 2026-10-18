package com.zhubao.erp.document.domain;

import java.util.List;

public record DocumentAttachmentPage(
        List<Entry> entries,
        boolean hasMore
) {
    public DocumentAttachmentPage {
        entries = List.copyOf(entries);
    }

    public record Entry(long id, EvidenceAttachment attachment) {
    }
}
