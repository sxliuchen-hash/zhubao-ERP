package com.zhubao.erp.finance.domain;

/**
 * 一次资产交接。空值表示当时该项仍待补。不记录原值或折旧。
 */
public record FixedAssetCustodyTransfer(
        String transferId,
        String assetCode,
        String fromLocation,
        String toLocation,
        String fromCustodian,
        String toCustodian,
        String fromStatus,
        String toStatus,
        long version
) {
}
