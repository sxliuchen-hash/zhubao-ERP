package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 从原交付收回的一份待复核来源。不记录费用金额或工资，未知重量不能写成 0。
 */
public record CustomerReturnSource(
        String returnId,
        String requestId,
        String deliveryId,
        String outboundCode,
        String bagId,
        BigDecimal quantity,
        BigDecimal weight,
        CustomerReturnStatus status,
        String adjustmentBasis,
        int version
) {
    public static final String UNDECIDED_BASIS = "退回返修不另收加工费，也不另计工资";
    private static final Pattern RETURN_ID = Pattern.compile("^CR[0-9a-f]{32}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public CustomerReturnSource {
        if (returnId == null || !RETURN_ID.matcher(returnId).matches()) {
            throw new BusinessException("退货编号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        if (deliveryId == null || !deliveryId.matches("^FH-\\d{8}-\\d{4}$")) {
            throw new BusinessException("交付单号无效");
        }
        if (outboundCode == null || outboundCode.isBlank()) {
            throw new BusinessException("退货必须关联已确认交付");
        }
        outboundCode = outboundCode.trim();
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("缺少交付袋");
        }
        bagId = bagId.trim();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("退货数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
        if (weight == null || weight.signum() <= 0) {
            throw new BusinessException("需要本份额的实际重量后才能转移或发货");
        }
        weight = StockMeasurement.processingScale(weight);
        if (status != CustomerReturnStatus.PENDING_REVIEW) {
            throw new BusinessException("退回不能自动记成合格");
        }
        adjustmentBasis = adjustmentBasis == null ? "" : adjustmentBasis.trim();
        if (!adjustmentBasis.contains(UNDECIDED_BASIS) || adjustmentBasis.contains("0元")) {
            throw new BusinessException("退回返修不另收加工费，也不另计工资");
        }
        if (version < 1) {
            throw new BusinessException("退货版本不正确");
        }
    }
}
