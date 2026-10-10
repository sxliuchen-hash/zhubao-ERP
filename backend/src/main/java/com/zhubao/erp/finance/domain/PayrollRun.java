package com.zhubao.erp.finance.domain;

/**
 * 某月工资确认。确认后该月不能再重算。
 */
public record PayrollRun(
        String yearMonth,
        boolean confirmed,
        String confirmedBy,
        String confirmedAt
) {
}
