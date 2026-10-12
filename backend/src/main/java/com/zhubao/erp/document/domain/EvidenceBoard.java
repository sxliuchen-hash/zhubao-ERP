package com.zhubao.erp.document.domain;

import java.util.List;

/**
 * 一张单据上的证据：已关联引用，以及来源不确定或尚未关联的历史文件。
 */
public record EvidenceBoard(
        List<EvidenceLinkView> links,
        List<HistoricalEvidenceView> historical
) {
}
