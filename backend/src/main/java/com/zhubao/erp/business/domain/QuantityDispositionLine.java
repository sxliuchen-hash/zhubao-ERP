package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/** 一袋在一道工序上的一次数量处置。待核单独保存，不并进损耗。 */
public record QuantityDispositionLine(
        String dispositionId,
        String requestId,
        String bagId,
        String stepId,
        BigDecimal handoff,
        BigDecimal loss,
        BigDecimal scrap,
        BigDecimal returned,
        BigDecimal pending
) {
}
