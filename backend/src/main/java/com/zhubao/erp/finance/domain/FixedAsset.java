package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 固定资产卡片。月折旧只进公司成本，不摊到客户。
 */
public record FixedAsset(
        String code,
        String name,
        String category,
        BigDecimal originalValue,
        BigDecimal residualValue,
        String startMonth,
        int usefulMonths,
        String operator,
        String createdAt
) {
}
