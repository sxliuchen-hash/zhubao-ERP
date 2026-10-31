package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 处置读取结果。净值和折旧影响未核定时为空或待核，不用 0 或默认月份代替。
 * 回款金额只在关联了已经存在的收款单时出现。
 */
public record FixedAssetDispositionView(
        String dispositionId,
        String assetCode,
        BigDecimal originalValue,
        String kind,
        String kindLabel,
        String occurredOn,
        String basis,
        String depreciationImpact,
        String depreciationImpactLabel,
        BigDecimal netValue,
        String receiptCode,
        BigDecimal proceedsAmount,
        boolean received
) {
}
