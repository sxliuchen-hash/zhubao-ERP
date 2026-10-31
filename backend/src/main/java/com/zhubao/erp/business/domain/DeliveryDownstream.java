package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 已发生的下游占用。没有记录表示还没有这笔凭据，不是金额或数量为 0 的费用。
 */
public interface DeliveryDownstream {

    BigDecimal consumedQuantity(String sourceCode);

    DownstreamFact chargeFact(String deliveryId);

    DownstreamFact refundFact(String deliveryId);
}
