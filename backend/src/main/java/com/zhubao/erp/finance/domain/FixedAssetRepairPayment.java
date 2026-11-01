package com.zhubao.erp.finance.domain;

/** 单次维修付款的凭据，关联真实付款号，与维修费用凭据分别保留。 */
public record FixedAssetRepairPayment(String paymentCode, String voucher) {
}
