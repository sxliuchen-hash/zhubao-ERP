package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 某资产某月的折旧。同一资产同一月只生成一次。
 */
public record DepreciationEntry(
        String code,
        String assetCode,
        String yearMonth,
        BigDecimal amount
) {
}
