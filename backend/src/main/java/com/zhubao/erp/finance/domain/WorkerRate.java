package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 工人计件单价。与客户加工费协议分开维护。
 */
public record WorkerRate(
        String code,
        String processName,
        ChargeUnit unit,
        BigDecimal unitPrice,
        String effectiveFrom,
        String effectiveTo,
        String operator,
        String createdAt
) {
    public WorkerRate {
        effectiveTo = effectiveTo == null ? "" : effectiveTo;
    }

    public WorkerRate closeAt(String effectiveTo) {
        return new WorkerRate(code, processName, unit, unitPrice, effectiveFrom, effectiveTo, operator, createdAt);
    }
}
