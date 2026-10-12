package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 一次质检轮次。初检和复检都留独立记录，不改写上一轮的条款、原因和冻结。
 */
public record QualityRound(
        String roundCode,
        String qualityCaseCode,
        String orderCode,
        String motherOrderCode,
        int roundNo,
        String roundType,
        String reworkOrderCode,
        String reworkReportLedger,
        String outcome,
        boolean frozen,
        String ledgerCode,
        String inspector,
        String createdAt,
        List<QualityCheckLine> lines
) {
    public static final String INITIAL = "INITIAL";
    public static final String RECHECK = "RECHECK";

    public QualityRound {
        lines = lines == null ? List.of() : List.copyOf(lines);
        reworkOrderCode = reworkOrderCode == null ? "" : reworkOrderCode;
        reworkReportLedger = reworkReportLedger == null ? "" : reworkReportLedger;
        ledgerCode = ledgerCode == null ? "" : ledgerCode;
        roundCode = roundCode == null ? "" : roundCode;
        roundType = roundType == null ? "" : roundType;
    }
}
