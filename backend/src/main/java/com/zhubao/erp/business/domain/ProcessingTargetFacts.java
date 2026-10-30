package com.zhubao.erp.business.domain;

/**
 * 普通加工目标上已经存在的客户和数量单位。打样、金工不从这里取值。
 */
public record ProcessingTargetFacts(Long customerId, String quantityUnit) {
}
