package com.zhubao.erp.finance.domain;

/**
 * 固定资产购置来源。原值只在资产卡片上记一次；这里不另存一份原值。
 * 没有付款凭据时付款单号为空，不能据此当成已付。
 */
public record FixedAssetAcquisition(
        String acquisitionId,
        String assetCode,
        FixedAssetAcquisitionSource source,
        FixedAssetAmountState amountState,
        String supplierName,
        String acquiredOn,
        String handoverLocation,
        String handoverReceiver,
        String payableCode,
        String coveredPayableCode,
        String paymentCode,
        String paymentVoucher
) {
}
