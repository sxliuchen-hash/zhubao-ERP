package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 质量查询。返工不表示成色或收费已改。 */
public record GoldworkQualityView(
        String qualityId,
        String goldworkCode,
        String reportId,
        String priorQualityId,
        int roundNo,
        String outcome,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String note,
        boolean purityChanged,
        boolean feeChanged
) {
}
