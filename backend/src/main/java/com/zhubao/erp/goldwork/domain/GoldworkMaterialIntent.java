package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.common.error.BusinessException;

/** 金工单上声明的材料安排。只说明来源类型，不代替真实收料。 */
public enum GoldworkMaterialIntent {
    CUSTOMER_SUPPLIED("客户来料"),
    COMPANY_PREPARED("公司自备");

    private final String label;

    GoldworkMaterialIntent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GoldworkMaterialIntent parse(String raw) {
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
