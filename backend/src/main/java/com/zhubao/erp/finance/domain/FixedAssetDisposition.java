package com.zhubao.erp.finance.domain;

/**
 * 资产停用、报废或处置事实。不删除原卡，也不在这里计算净值或停折旧月份。
 */
public record FixedAssetDisposition(
        String dispositionId,
        String assetCode,
        String kind,
        String occurredOn,
        String basis,
        String receiptCode
) {
    public static final String STOP = "STOP";
    public static final String SCRAP = "SCRAP";
    public static final String DISPOSE = "DISPOSE";
    public static final String IMPACT_PENDING = "PENDING";
}
