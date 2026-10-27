package com.zhubao.erp.finance.domain;

/** 结单引用的交货、批次、轮次、损耗或旧账。快照只作核对，不代替库存流水。 */
public record OrderSettlementSource(
        int lineNo,
        String sourceKind,
        String sourceCode,
        String snapshotText
) {
    public OrderSettlementSource {
        sourceKind = sourceKind == null ? "" : sourceKind;
        sourceCode = sourceCode == null ? "" : sourceCode;
        snapshotText = snapshotText == null ? "" : snapshotText;
    }
}
