package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 一次交付的样费决定。免收不保存 0 元。 */
public record SamplingFeeDecision(
        String feeId,
        String deliveryId,
        String samplingCode,
        String decision,
        BigDecimal amount,
        String reason,
        String receivableCode
) {
    public SamplingFeeDecision {
        if (feeId == null || feeId.isBlank() || deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("样费决定不完整");
        }
        feeId = feeId.trim();
        deliveryId = deliveryId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("样费必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (!"CHARGE".equals(decision) && !"WAIVE".equals(decision)) {
            throw new BusinessException("请确认样费或明确免收");
        }
        reason = reason == null ? "" : reason.trim();
        receivableCode = receivableCode == null ? "" : receivableCode.trim();
        if ("WAIVE".equals(decision)) {
            if (reason.isEmpty()) {
                throw new BusinessException("请填写免收原因");
            }
            if (amount != null) {
                throw new BusinessException("免收不记一笔零元应收");
            }
            if (!receivableCode.isEmpty()) {
                throw new BusinessException("免收不记一笔零元应收");
            }
        } else {
            if (amount == null || amount.signum() <= 0) {
                throw new BusinessException(amount != null && amount.signum() == 0 ? "未知金额不能写成 0" : "样费必须大于零");
            }
            if (amount.scale() > 2) {
                throw new BusinessException("样费最多两位小数，不能截断后入账");
            }
            if (receivableCode.isEmpty()) {
                throw new BusinessException("缺少应收编号");
            }
        }
        if (reason.length() > 200) {
            throw new BusinessException("免收原因超过可保存长度");
        }
    }
}
