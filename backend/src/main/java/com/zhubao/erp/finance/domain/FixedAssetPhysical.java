package com.zhubao.erp.finance.domain;

/**
 * 挂在原资产编码上的实物字段。不保存原值、残值或折旧。
 * 空值表示尚未登记，读取时标待补，不写成历史事实。
 */
public record FixedAssetPhysical(
        String assetCode,
        String modelName,
        String serialNumber,
        String locationName,
        String custodianName,
        String useStatus,
        long version
) {
}
