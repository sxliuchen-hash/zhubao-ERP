package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.common.error.BusinessException;

/** 选料与加工上的一项选择。雕刻和车型能否同时办理尚未核定。 */
public enum SamplingMakingChoice {
    CARVE("雕刻"),
    SHAPE("车型");

    private final String label;

    SamplingMakingChoice(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SamplingMakingChoice parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择雕刻或车型");
        }
    }
}
