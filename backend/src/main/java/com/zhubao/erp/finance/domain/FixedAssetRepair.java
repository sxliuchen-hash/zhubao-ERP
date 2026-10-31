package com.zhubao.erp.finance.domain;

/**
 * 资产维修记录。金额只在确认后的应付上出现一次，这里不另存成本。
 */
public record FixedAssetRepair(
        String repairId,
        String assetCode,
        String repairedOn,
        String handoverName,
        String evidenceCode,
        FixedAssetRepairState state,
        String payableCode
) {
}
