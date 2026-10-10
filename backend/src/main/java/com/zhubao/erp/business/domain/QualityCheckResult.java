package com.zhubao.erp.business.domain;

import java.util.List;

/**
 * 一次质检对照的提交结果。inspector 是登录账号的操作人。
 */
public record QualityCheckResult(
        String qualityCaseCode,
        String orderCode,
        String motherOrderCode,
        String inspector,
        String outcome,
        boolean frozen,
        String ledgerCode,
        String reworkOrderCode,
        List<QualityCheckLine> lines
) {
    public QualityCheckResult {
        lines = lines == null ? List.of() : List.copyOf(lines);
        ledgerCode = ledgerCode == null ? "" : ledgerCode;
        reworkOrderCode = reworkOrderCode == null ? "" : reworkOrderCode;
    }
}
