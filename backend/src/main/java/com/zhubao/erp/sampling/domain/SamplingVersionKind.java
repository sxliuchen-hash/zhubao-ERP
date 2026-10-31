package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.common.error.BusinessException;

/** 打样图稿的一类版本。平设、模型和珠型分开编号。 */
public enum SamplingVersionKind {
    FLAT("平设"),
    MODEL("模型"),
    BEAD("珠型");

    private final String label;

    SamplingVersionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SamplingVersionKind parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请选择平设、模型或珠型");
        }
        try {
            return valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择平设、模型或珠型");
        }
    }
}
