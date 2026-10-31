package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 回收查询。不并进成品，也不折抵费用。 */
public record GoldworkRecoveryView(
        String recoveryId,
        String goldworkCode,
        String sourceLineId,
        String outputMovementId,
        String kind,
        String owner,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState,
        boolean postedToLedger,
        boolean feeOffset
) {
}
