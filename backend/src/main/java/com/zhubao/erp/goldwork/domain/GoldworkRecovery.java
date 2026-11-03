package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;

import java.math.BigDecimal;

/** 回收、余料、实物报废或不可恢复损耗。重量未知时保持为空，不写成 0。 */
public record GoldworkRecovery(
        String recoveryId,
        String goldworkCode,
        String sourceLineId,
        String outputMovementId,
        GoldworkRecoveryKind kind,
        OwnerType owner,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState
) {
    public GoldworkRecovery {
        if (recoveryId == null || recoveryId.isBlank()) {
            throw new BusinessException("回收记录编号无效");
        }
        recoveryId = recoveryId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("回收必须挂在金工单上");
        }
        goldworkCode = reference.code();
        sourceLineId = required(sourceLineId, "请选择金料来源");
        outputMovementId = required(outputMovementId, "请指明对应成品产出");
        if (kind == null) {
            throw new BusinessException("请选择回收去向");
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
        if (!"UNDECIDED".equals(precisionState)) {
            throw new BusinessException("本版金工重量只作记录，不能按库存精度入账");
        }
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }
}
