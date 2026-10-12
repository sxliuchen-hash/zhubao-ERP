package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 扫贴纸后看到的质检对照。ready 为 false 时不能提交。
 */
public record QualityCheckView(
        String orderCode,
        String motherOrderCode,
        String customerName,
        String dueDate,
        String processingRequirement,
        boolean ready,
        String blockReason,
        String inspector,
        String qualityCaseCode,
        String outcome,
        boolean frozen,
        String reworkOrderCode,
        List<QualityCheckLine> lines,
        String roundCode,
        int roundNo,
        String roundType,
        boolean reworkReported,
        boolean canRecheck,
        String recheckBlock,
        boolean canRelease,
        String releaseBlock,
        List<QualityRound> rounds
) {
    public QualityCheckView {
        lines = lines == null ? List.of() : List.copyOf(lines);
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
        roundCode = roundCode == null ? "" : roundCode;
        roundType = roundType == null ? "" : roundType;
        recheckBlock = recheckBlock == null ? "" : recheckBlock;
        releaseBlock = releaseBlock == null ? "" : releaseBlock;
    }

    public QualityCheckView(
            String orderCode,
            String motherOrderCode,
            String customerName,
            String dueDate,
            String processingRequirement,
            boolean ready,
            String blockReason,
            String inspector,
            String qualityCaseCode,
            String outcome,
            boolean frozen,
            String reworkOrderCode,
            List<QualityCheckLine> lines
    ) {
        this(orderCode, motherOrderCode, customerName, dueDate, processingRequirement, ready, blockReason,
                inspector, qualityCaseCode, outcome, frozen, reworkOrderCode, lines,
                "", 0, "", false, false, "", false, "", List.of());
    }
}
