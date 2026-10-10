package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 已经提交的质检对照。合格和让步只留这份记录；不合格还会另写冻结台账。
 */
public record QualityRequirementCheck(
        String qualityCaseCode,
        String orderCode,
        String motherOrderCode,
        String inspector,
        String outcome,
        boolean frozen,
        String ledgerCode,
        String reworkOrderCode,
        String createdAt,
        List<QualityCheckLine> lines
) {
    public QualityRequirementCheck {
        lines = lines == null ? List.of() : List.copyOf(lines);
        ledgerCode = ledgerCode == null ? "" : ledgerCode;
        reworkOrderCode = reworkOrderCode == null ? "" : reworkOrderCode;
    }

    public QualityRequirementCheck withRework(String reworkOrderCode) {
        return new QualityRequirementCheck(
                qualityCaseCode,
                orderCode,
                motherOrderCode,
                inspector,
                outcome,
                frozen,
                ledgerCode,
                reworkOrderCode,
                createdAt,
                lines
        );
    }
}
