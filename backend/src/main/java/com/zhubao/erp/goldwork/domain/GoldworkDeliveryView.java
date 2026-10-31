package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 交付查询。未确认费用不是应收，也不是销售收入。 */
public record GoldworkDeliveryView(
        String deliveryId,
        String goldworkCode,
        String qualityId,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String feeState,
        boolean receivablePosted,
        boolean salesPosted,
        boolean ordinaryShipment
) {
}
