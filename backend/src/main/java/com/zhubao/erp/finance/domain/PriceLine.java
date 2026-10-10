package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 协议价目。按克、按件时工序名为空；按工序时写明雕刻、镶嵌或抛光。
 */
public record PriceLine(
        String processName,
        ChargeUnit unit,
        BigDecimal unitPrice
) {
}
