package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 消耗品当前结存。由采购和领用汇总，不改客户金料台账。
 */
public record ConsumableStockView(
        String code,
        String name,
        String unitName,
        BigDecimal onHandQuantity,
        BigDecimal onHandAmount
) {
}
