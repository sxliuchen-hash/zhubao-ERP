package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

public record LossApprovalResult(
        String approvalCode,
        String motherOrderCode,
        String workOrderCode,
        String process,
        BigDecimal inputWeight,
        BigDecimal outputWeight,
        BigDecimal reasonableTransferWeight,
        BigDecimal lossWeight,
        BigDecimal allowedLossWeight,
        BigDecimal excessLossWeight,
        BigDecimal standardLossRatePercent,
        BigDecimal actualLossRatePercent,
        boolean overLimit,
        String approvalStatus,
        BigDecimal acceptedQuantity,
        BigDecimal pieceRate,
        BigDecimal pieceWage,
        BigDecimal lossDeduction,
        BigDecimal trialWage,
        boolean formalPayroll,
        String operator,
        String approver,
        String evidenceCode
) {
}
