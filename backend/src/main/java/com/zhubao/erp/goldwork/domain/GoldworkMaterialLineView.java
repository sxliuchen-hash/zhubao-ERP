package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 金料查询。空的件数、净重、毛重和成色表示未核定，不是 0。 */
public record GoldworkMaterialLineView(
        String lineId,
        String goldworkCode,
        String owner,
        String sourceId,
        Integer sourceLineNo,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState,
        boolean postedToLedger
) {
}
