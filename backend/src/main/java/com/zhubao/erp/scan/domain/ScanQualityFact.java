package com.zhubao.erp.scan.domain;

import java.util.List;

/** 已经保存的质检案件。原不合格结果保持原样。 */
public record ScanQualityFact(
        String qualityCaseCode,
        String orderCode,
        String outcome,
        boolean frozen,
        String reworkOrderCode,
        String inspector,
        List<ScanQualityLineFact> lines
) {
    public ScanQualityFact {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
