package com.zhubao.erp.business.domain;

/**
 * 一条加工要求的核对结果。尚未判定时，判定和原因为空。
 */
public record QualityCheckLine(
        int lineNo,
        String clause,
        String verdict,
        String reason
) {
    public QualityCheckLine {
        clause = clause == null ? "" : clause;
        verdict = verdict == null ? "" : verdict;
        reason = reason == null ? "" : reason;
    }
}
