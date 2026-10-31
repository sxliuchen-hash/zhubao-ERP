package com.zhubao.erp.sampling.domain;

/**
 * 打样单查询结果。不返回数量或金额，避免把尚未发生的份额和样费写成 0。
 * 也不返回母单号。
 */
public record SamplingOrderView(
        String samplingCode,
        String businessReference,
        long customerId,
        String customerName,
        String materialIntent,
        String materialIntentLabel,
        String requirement,
        String ownerName,
        String status,
        String statusLabel,
        long version,
        String openedBy,
        String openedOn
) {
}
