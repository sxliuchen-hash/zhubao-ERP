package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 已放行份额的交付。费用保持未确认，不按克数乘金价。 */
public record GoldworkDelivery(
        String deliveryId,
        String goldworkCode,
        String qualityId,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String feeState
) {
    public GoldworkDelivery {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("交付编号无效");
        }
        deliveryId = deliveryId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("交付必须挂在金工单上");
        }
        goldworkCode = reference.code();
        qualityId = qualityId == null ? "" : qualityId.trim();
        if (qualityId.isEmpty()) {
            throw new BusinessException("请选择已放行份额");
        }
        pieceCount = GoldworkMeasurement.declared(pieceCount, "未知件数不能写成 0");
        netWeight = GoldworkMeasurement.declared(netWeight, "未知重量不能写成 0");
        if (pieceCount == null && netWeight == null) {
            throw new BusinessException("请填写本次交付份额");
        }
        weightUnit = weightUnit == null ? "" : weightUnit.trim();
        if (netWeight != null && weightUnit.isEmpty()) {
            throw new BusinessException("请填写重量单位");
        }
        purity = purity == null ? "" : purity.trim();
        if (!"UNCONFIRMED".equals(feeState)) {
            throw new BusinessException("金工本版只记录件数，不核算费用");
        }
    }
}
