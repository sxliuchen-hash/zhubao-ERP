package com.zhubao.erp.sampling.domain;

import java.math.BigDecimal;

/**
 * 一次交付的结果。订单保持进行中。样费未确认时不返回 0。
 */
public record SamplingDeliveryView(
        String deliveryId,
        String samplingCode,
        String versionKind,
        int versionNo,
        String materialUseId,
        String sourceOwner,
        BigDecimal quantity,
        String unit,
        String credentialCode,
        String deliveredOn,
        BigDecimal remainingQuantity,
        boolean orderClosed,
        String orderStatus,
        String feeState
) {
}
