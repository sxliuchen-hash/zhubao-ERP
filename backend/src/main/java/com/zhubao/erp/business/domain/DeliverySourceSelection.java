package com.zhubao.erp.business.domain;

/**
 * 草稿所选的一袋。数量和重量以袋上的当前事实为准，调用方不能另报一套数量。
 */
public record DeliverySourceSelection(String bagId, int bagVersion, String targetCode, String quantityUnit) {
}
