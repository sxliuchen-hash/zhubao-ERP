package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.DeliveryDownstream;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 客户账单和退款还没有挂到交付单上。没有记录不等于费用已经按 0 确认。
 */
@Component
public class UnrecordedDeliveryDownstream implements DeliveryDownstream {

    @Override
    public BigDecimal consumedQuantity(String sourceCode) {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean hasConfirmedCharge(String deliveryId) {
        return false;
    }

    @Override
    public boolean hasConfirmedRefund(String deliveryId) {
        return false;
    }
}
