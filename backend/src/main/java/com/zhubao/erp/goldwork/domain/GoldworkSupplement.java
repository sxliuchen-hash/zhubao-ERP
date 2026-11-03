package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;

import java.math.BigDecimal;

/** 一笔独立补料或退补。不覆盖最初来料行。 */
public record GoldworkSupplement(
        String supplementId,
        String goldworkCode,
        String originalLineId,
        String priorSupplementId,
        GoldworkSupplementDirection direction,
        OwnerType owner,
        String sourceId,
        Integer sourceLineNo,
        String purpose,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        String weightUnit,
        String purity,
        String precisionState
) {
    public GoldworkSupplement {
        if (supplementId == null || supplementId.isBlank()) {
            throw new BusinessException("补料编号无效");
        }
        supplementId = supplementId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("补料必须挂在金工单上");
        }
        goldworkCode = reference.code();
        originalLineId = required(originalLineId, "请选择最初来料");
        priorSupplementId = priorSupplementId == null ? "" : priorSupplementId.trim();
        if (direction == null) {
            throw new BusinessException("请选择补料或退补");
        }
        if (direction == GoldworkSupplementDirection.ISSUE && !priorSupplementId.isEmpty()) {
            throw new BusinessException("补料不引用另一笔补料");
        }
        if (direction == GoldworkSupplementDirection.RETURN && priorSupplementId.isEmpty()) {
            throw new BusinessException("退补必须指向一笔补料");
        }
        if (owner != OwnerType.CUSTOMER && owner != OwnerType.COMPANY) {
            throw new BusinessException("金料权属无效");
        }
        sourceId = required(sourceId, "请选择真实来源");
        purpose = required(purpose, "请填写补料用途");
        if (purpose.length() > 200) {
            throw new BusinessException("补料用途超过可保存长度");
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
