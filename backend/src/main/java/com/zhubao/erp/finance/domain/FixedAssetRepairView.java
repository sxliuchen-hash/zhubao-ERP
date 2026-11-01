package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 维修读取结果。维修中只说明进度，不表示报废。已付只来自真实付款。
 */
public record FixedAssetRepairView(
        String repairId,
        String assetCode,
        BigDecimal originalValue,
        String repairedOn,
        String handoverLabel,
        String evidenceLabel,
        String state,
        String stateLabel,
        String payableCode,
        BigDecimal costAmount,
        BigDecimal paidAmount,
        boolean paid,
        java.util.List<FixedAssetRepairPayment> payments
) {
}
