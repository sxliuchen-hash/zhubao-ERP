package com.zhubao.erp.finance.domain;

import java.util.List;

/**
 * 客户加工费协议。改价只影响之后的入账，已生成账单保留当时快照。
 */
public record PriceAgreement(
        String code,
        long customerId,
        String customerName,
        PricingMode mode,
        String effectiveFrom,
        String effectiveTo,
        List<PriceLine> lines,
        String operator,
        String createdAt
) {
    public PriceAgreement {
        lines = List.copyOf(lines);
        effectiveTo = effectiveTo == null ? "" : effectiveTo;
    }

    public PriceAgreement closeAt(String effectiveTo) {
        return new PriceAgreement(
                code,
                customerId,
                customerName,
                mode,
                effectiveFrom,
                effectiveTo,
                lines,
                operator,
                createdAt
        );
    }
}
