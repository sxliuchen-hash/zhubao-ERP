package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 投入产出查询。空的净重不是毛重，也不是损耗。 */
public record GoldworkMovementView(
        String movementId,
        String goldworkCode,
        String stepId,
        String kind,
        String sourceLineId,
        String owner,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String disposition,
        String conversionNote,
        String precisionState,
        boolean postedToLedger
) {
}
