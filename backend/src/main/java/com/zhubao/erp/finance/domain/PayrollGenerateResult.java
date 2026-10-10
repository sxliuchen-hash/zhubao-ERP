package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 月度工资试算结果。缺工人单价的工序不会入账。
 */
public record PayrollGenerateResult(
        String yearMonth,
        List<PayrollLine> lines,
        List<UnpricedItem> missingRates
) {
    public PayrollGenerateResult {
        lines = List.copyOf(lines);
        missingRates = List.copyOf(missingRates);
    }
}
