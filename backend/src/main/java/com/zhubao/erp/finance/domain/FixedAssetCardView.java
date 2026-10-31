package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/**
 * 原财务卡片加上实物字段的读取结果。原值只引用原卡片，不另存一份。
 */
public record FixedAssetCardView(
        String code,
        String name,
        String category,
        BigDecimal originalValue,
        BigDecimal residualValue,
        String startMonth,
        int usefulMonths,
        FixedAssetField model,
        FixedAssetField serialNumber,
        FixedAssetField location,
        FixedAssetField custodian,
        FixedAssetField useStatus,
        long version
) {
}
