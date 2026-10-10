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
        List<QualityCheckLine> lines
) {
    public QualityCheckView {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
