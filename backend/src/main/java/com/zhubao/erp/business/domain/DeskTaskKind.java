package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 轻任务类型。不是现场工序，不参与报工、质检或库存。
 */
public enum DeskTaskKind {
    MODELING("建模"),
    DESIGN("设计"),
    OTHER("其他");

    private final String label;

    DeskTaskKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DeskTaskKind parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请选择待办类型");
        }
        for (DeskTaskKind kind : values()) {
            if (kind.name().equalsIgnoreCase(text) || kind.label.equals(text)) {
                return kind;
            }
        }
        throw new BusinessException("未知待办类型：" + text);
    }
}
