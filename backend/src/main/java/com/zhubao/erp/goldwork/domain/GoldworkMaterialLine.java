package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;

import java.math.BigDecimal;

/**
 * 金工来料引用。不另记实物余额。未填的件数和重量保持为空。
 */
public record GoldworkMaterialLine(
        String lineId,
        String goldworkCode,
        OwnerType owner,
        String sourceId,
        Integer sourceLineNo,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState
) {
    public GoldworkMaterialLine {
        if (lineId == null || lineId.isBlank()) {
            throw new BusinessException("金料明细编号无效");
        }
        lineId = lineId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("金料必须挂在金工单上");
        }
        goldworkCode = reference.code();
        if (owner != OwnerType.CUSTOMER && owner != OwnerType.COMPANY) {
            throw new BusinessException("金料权属无效");
        }
        sourceId = sourceId == null ? "" : sourceId.trim();
        if (sourceId.isEmpty()) {
            throw new BusinessException("请选择真实来源");
        }
        pieceCount = GoldworkMeasurement.declared(pieceCount, "未知件数不能写成 0");
        netWeight = GoldworkMeasurement.declared(netWeight, "未知重量不能写成 0");
        grossWeight = GoldworkMeasurement.declared(grossWeight, "未知重量不能写成 0");
        weightUnit = weightUnit == null ? "" : weightUnit.trim();
        if ((netWeight != null || grossWeight != null) && weightUnit.isEmpty()) {
            throw new BusinessException("请填写重量单位");
        }
        purity = purity == null ? "" : purity.trim();
        if (!"UNDECIDED".equals(precisionState)) {
            throw new BusinessException("金工计量精度尚未核定，不能入账");
        }
    }
}
