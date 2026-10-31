package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 打样单上声明的材料安排。只说明客户来料或公司自备，不代替真实领料来源。
 */
public enum SamplingMaterialIntent {
    CUSTOMER_SUPPLIED("客户来料"),
    COMPANY_PREPARED("公司自备");

    private final String label;

    SamplingMaterialIntent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SamplingMaterialIntent parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请选择来料或公司自备");
        }
        try {
            return valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("请选择来料或公司自备");
        }
    }
}
