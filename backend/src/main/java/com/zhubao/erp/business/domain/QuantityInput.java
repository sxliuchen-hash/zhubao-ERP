package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;

/**
 * 数量输入。未传来源按手填。拍照计数只保留引用，当前不调用识别算法。
 */
public record QuantityInput(
        Source source,
        BigDecimal confirmedQuantity,
        BigDecimal recognizedQuantity,
        String resultReference
) {
    public enum Source {
        MANUAL,
        PHOTO_COUNT
    }

    public QuantityInput {
        source = source == null ? Source.MANUAL : source;
        if (confirmedQuantity == null || confirmedQuantity.signum() < 0) {
            throw new BusinessException("确认数量不能为空或为负");
        }
        if (source == Source.PHOTO_COUNT && (resultReference == null || resultReference.isBlank())) {
            throw new BusinessException("拍照计数尚未接入，请改用手填数量");
        }
        resultReference = resultReference == null ? "" : resultReference.trim();
    }

    public static QuantityInput manual(BigDecimal confirmedQuantity) {
        return new QuantityInput(Source.MANUAL, confirmedQuantity, null, "");
    }
}
