package com.zhubao.erp.sampling.domain;

import java.math.BigDecimal;

/** 样费结果。免收时金额和应收编号为空。不返回工资。 */
public record SamplingFeeView(
        String feeId,
        String deliveryId,
        String samplingCode,
        String decision,
        BigDecimal amount,
        String reason,
        String receivableCode,
        String motherOrderCode,
        boolean wagePosted
) {
}
