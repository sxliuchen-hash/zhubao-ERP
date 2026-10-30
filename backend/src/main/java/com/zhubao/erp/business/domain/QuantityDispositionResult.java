package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 一次处置的结果。本批已交工、已到货部分处理完、整道关闭是三件不同的事。
 */
public record QuantityDispositionResult(
        String dispositionId,
        String bagId,
        String stepId,
        BigDecimal handoff,
        BigDecimal loss,
        BigDecimal scrap,
        BigDecimal returned,
        BigDecimal pending,
        BigDecimal unprocessed,
        boolean batchHanded,
        boolean arrivedPortionFinished,
        boolean stepClosed,
        boolean weightPending,
        String stockDocumentType,
        String remainderBagId,
        String handoffBagId,
        String pendingBagId
) {
}
