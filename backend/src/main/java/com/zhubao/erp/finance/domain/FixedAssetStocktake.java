package com.zhubao.erp.finance.domain;

/**
 * 一张资产的盘点快照。原值不在这里改写。未见或未扫也不等于报废。
 */
public record FixedAssetStocktake(
        String stocktakeId,
        String assetCode,
        String countedAt,
        String bookLocation,
        String bookCustodian,
        String bookStatus,
        long bookVersion,
        String seen,
        String countLocation,
        String countCustodian,
        String exceptionText,
        String status,
        long version
) {
    public static final String OPEN = "OPEN";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PRESENT = "PRESENT";
    public static final String ABSENT = "ABSENT";
}
