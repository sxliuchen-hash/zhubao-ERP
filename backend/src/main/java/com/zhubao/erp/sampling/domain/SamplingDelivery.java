package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/** 一次样品交付。不表示整单完成，也不写成普通发货。 */
public record SamplingDelivery(
        String deliveryId,
        String samplingCode,
        SamplingVersionKind versionKind,
        int versionNo,
        String materialUseId,
        BigDecimal quantity,
        String unit,
        String credentialCode,
        String deliveredOn
) {
    public SamplingDelivery {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new BusinessException("交付编号无效");
        }
        deliveryId = deliveryId.trim();
        BusinessTargetReference reference = BusinessTargetReference.fromCode(samplingCode);
        if (reference.kind() != BusinessKind.SAMPLING) {
            throw new BusinessException("交付必须挂在打样单上");
        }
        samplingCode = reference.code();
        if (versionKind == null || versionNo < 1) {
            throw new BusinessException("请选择已确认版本");
        }
        materialUseId = materialUseId == null ? "" : materialUseId.trim();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("交付数量必须大于零");
        }
        unit = unit == null ? "" : unit.trim();
        if (unit.isEmpty()) {
            throw new BusinessException("请填写单位");
        }
        credentialCode = SamplingVersion.referenceCode(credentialCode, "交付凭据");
        if (credentialCode.isEmpty()) {
            throw new BusinessException("请填写交付凭据");
        }
        deliveredOn = deliveredOn == null ? "" : deliveredOn.trim();
        if (!deliveredOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("交付日期无效");
        }
    }
}
