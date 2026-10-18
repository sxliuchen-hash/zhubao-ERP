package com.zhubao.erp.document.domain;

import java.util.List;

public record DocumentPackagePage(
        List<Entry> entries,
        boolean hasMore
) {
    public DocumentPackagePage {
        entries = List.copyOf(entries);
    }

    public record Entry(long id, DocumentPackage documentPackage) {
    }
}
