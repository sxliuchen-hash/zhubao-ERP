package com.zhubao.erp.goldwork.domain;

import java.math.BigDecimal;

/** 金工报工查询。不代表已记库存或工资。 */
public record GoldworkOperationView(
        String reportId,
        String goldworkCode,
        String stepId,
        String stepLabel,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String attachmentCode,
        String workerName,
        String reportedOn,
        String precisionState,
        boolean postedToLedger,
        boolean payrollPosted
) {
}
