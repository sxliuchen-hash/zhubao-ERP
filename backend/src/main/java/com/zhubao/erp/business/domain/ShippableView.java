package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/** 本袋能否厂内发出。查询不写放行、库存或发货。 */
public record ShippableView(String bagId, BigDecimal quantity, List<String> blockingReasons) {
    public ShippableView {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }

    public boolean shippable() {
        return blockingReasons.isEmpty() && quantity.signum() > 0;
    }
}
