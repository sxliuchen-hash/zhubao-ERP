package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 某一报工份额的一轮质量结论。不改报工成色，也不改收费。 */
public record GoldworkQualityDecision(
        String qualityId,
        String goldworkCode,
        String reportId,
        String priorQualityId,
        int roundNo,
        GoldworkQualityOutcome outcome,
        BigDecimal pieceCount,
        BigDecimal netWeight,
        String weightUnit,
        String purity,
        String note
) {
    public GoldworkQualityDecision {
        if (qualityId == null || qualityId.isBlank()) {
            throw new BusinessException("质量记录编号无效");
        }
        qualityId = qualityId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(goldworkCode);
        if (reference.kind() != BusinessKind.GOLDWORK) {
            throw new BusinessException("质量记录必须挂在金工单上");
        }
        goldworkCode = reference.code();
        reportId = required(reportId, "请选择报工份额");
        priorQualityId = priorQualityId == null ? "" : priorQualityId.trim();
        if (roundNo < 1) {
            throw new BusinessException("质量轮次无效");
        }
        if (outcome == null) {
            throw new BusinessException("请选择放行、返工或待核");
        }
        pieceCount = GoldworkMeasurement.declared(pieceCount, "未知件数不能写成 0");
        netWeight = GoldworkMeasurement.declared(netWeight, "未知重量不能写成 0");
        if (pieceCount == null && netWeight == null) {
            throw new BusinessException("请填写本次检查份额");
        }
        weightUnit = weightUnit == null ? "" : weightUnit.trim();
        if (netWeight != null && weightUnit.isEmpty()) {
            throw new BusinessException("请填写重量单位");
        }
        purity = purity == null ? "" : purity.trim();
        note = note == null ? "" : note.trim();
        if (note.length() > 200) {
            throw new BusinessException("质量说明超过可保存长度");
        }
        if ((outcome == GoldworkQualityOutcome.REWORK || outcome == GoldworkQualityOutcome.PENDING) && note.isEmpty()) {
            throw new BusinessException("请填写返工或待核说明");
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
