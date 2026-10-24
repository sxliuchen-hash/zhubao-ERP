package com.zhubao.erp.label.domain;

import java.util.List;

/** 显式采用新保存要求的标签版本；保留业务原始来源快照。 */
public record LabelStorageEdition(String businessCode, int edition, List<StorageLabelSource> sources,
                                  String requestId, String reason, String operator, String createdAt) {
    public LabelStorageEdition { sources = List.copyOf(sources); }
}
