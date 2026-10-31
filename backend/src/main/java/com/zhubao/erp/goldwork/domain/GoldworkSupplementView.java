package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 补料查询。费用未核定，不在这里显示应收。 */
public record GoldworkSupplementView(
        String supplementId,
        String goldworkCode,
        String originalLineId,
        String priorSupplementId,
        String direction,
        String owner,
        String sourceId,
        Integer sourceLineNo,
        String purpose,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState,
        boolean postedToLedger,
        boolean feePosted
) {
}
