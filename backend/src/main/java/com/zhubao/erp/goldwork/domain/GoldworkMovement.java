package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;

import java.math.BigDecimal;

/**
 * 一道金工工序上的申报事实。净重不会从毛重抄来，也不按短重生成损耗。
 */
public record GoldworkMovement(
        String movementId,
        String goldworkCode,
        String stepId,
        GoldworkMovementKind kind,
        String sourceLineId,
        OwnerType owner,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String disposition,
        String conversionNote,
        String precisionState
) {
    public GoldworkMovement {
        if (movementId == null || movementId.isBlank()) {
            throw new BusinessException("投入产出编号无效");
        }
        movementId = movementId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("投入产出必须挂在金工单上");
        }
        goldworkCode = reference.code();
        stepId = stepId == null ? "" : stepId.trim();
        if (stepId.isEmpty()) {
            throw new BusinessException("请选择金工工序");
        }
        if (kind == null) {
            throw new BusinessException("请选择投入、产出、在制或处置");
        }
        sourceLineId = sourceLineId == null ? "" : sourceLineId.trim();
        if (sourceLineId.isEmpty()) {
            throw new BusinessException("请选择金料来源");
        }
        if (owner != OwnerType.CUSTOMER && owner != OwnerType.COMPANY) {
            throw new BusinessException("金料权属无效");
        }
        pieceCount = GoldworkMeasurement.declared(pieceCount, "未知件数不能写成 0");
        netWeight = GoldworkMeasurement.declared(netWeight, "未知重量不能写成 0");
        grossWeight = GoldworkMeasurement.declared(grossWeight, "未知重量不能写成 0");
        weightUnit = weightUnit == null ? "" : weightUnit.trim();
        if ((netWeight != null || grossWeight != null) && weightUnit.isEmpty()) {
            throw new BusinessException("请填写重量单位");
        }
        purity = purity == null ? "" : purity.trim();
        disposition = disposition == null ? "" : disposition.trim();
        conversionNote = conversionNote == null ? "" : conversionNote.trim();
        if (conversionNote.length() > 200) {
            throw new BusinessException("转换说明超过可保存长度");
        }
        if (!"UNDECIDED".equals(precisionState)) {
            throw new BusinessException("金工计量精度尚未核定，不能入账");
        }
    }
}
