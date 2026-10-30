package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/**
 * 一张已确认的客户按次账单。初始账单每个发货单一张，调整另记同根差额。
 */
public record CustomerDeliveryBill(
        String billId,
        String requestId,
        String rootBillId,
        String deliveryId,
        long customerId,
        CustomerDeliveryBillKind kind,
        BigDecimal totalAmount,
        int version
) {
    public CustomerDeliveryBill {
        if (billId == null || !billId.matches("^CB[0-9a-f]{32}$")) {
            throw new BusinessException("客户账单编号无效");
        }
        if (requestId == null || !requestId.matches("^[A-Za-z0-9._:-]{8,64}$")) {
            throw new BusinessException("请求编号无效");
        }
        if (rootBillId == null || rootBillId.isBlank()) {
            throw new BusinessException("客户账单缺少同根编号");
        }
        rootBillId = rootBillId.trim();
        if (deliveryId == null || !deliveryId.matches("^FH-\\d{8}-\\d{4}$")) {
            throw new BusinessException("交付单号无效");
        }
        if (customerId <= 0) {
            throw new BusinessException("客户账单缺少客户");
        }
        if (kind == null) {
            throw new BusinessException("客户账单种类无效");
        }
        if (totalAmount == null) {
            throw new BusinessException("客户账单金额无效");
        }
        if (totalAmount.signum() == 0) {
            throw new BusinessException("金额为 0 时不生成应收");
        }
        if (version < 1) {
            throw new BusinessException("客户账单版本不正确");
        }
    }
}
