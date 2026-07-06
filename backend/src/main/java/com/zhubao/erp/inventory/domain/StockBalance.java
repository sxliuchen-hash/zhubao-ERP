package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

public record StockBalance(
        StockDimension dimension,
        BigDecimal quantity,
        BigDecimal weight
) {
}
