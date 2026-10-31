package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 购置来源的读取结果。原值来自资产卡片；没有卡片时原值为空，不用 0 代替。
 * 已付只反映真实付款。未付款的应付显示已付 0，金额未齐则已付为空。
 */
public record FixedAssetAcquisitionView(
        String acquisitionId,
        String assetCode,
        String source,
        String amountState,
        BigDecimal originalValue,
        String supplierLabel,
        String acquiredOnLabel,
        String handoverLocationLabel,
        String handoverReceiverLabel,
        String payableCode,
        String coveredPayableCode,
        String paymentCode,
        BigDecimal paidAmount,
        boolean paid
) {
}
