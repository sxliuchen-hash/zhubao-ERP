package com.zhubao.erp.finance.domain;

/**
 * 实物字段的登记状态。待补表示历史没有这项事实，不能当成已经确认的空值。
 */
public record FixedAssetField(String presence, String value, String label) {

    public static final String PENDING = "PENDING";
    public static final String RECORDED = "RECORDED";

    public static FixedAssetField pending() {
        return new FixedAssetField(PENDING, "", "待补");
    }

    public static FixedAssetField recorded(String value) {
        return new FixedAssetField(RECORDED, value, value);
    }
}
