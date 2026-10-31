package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 交接历史的读取结果。原值只引用资产卡片，交接本身不产生新原值。
 */
public record FixedAssetCustodyView(
        String transferId,
        String assetCode,
        BigDecimal originalValue,
        String fromLocationLabel,
        String toLocationLabel,
        String fromCustodianLabel,
        String toCustodianLabel,
        String fromStatusLabel,
        String toStatusLabel,
        long version
) {
}
