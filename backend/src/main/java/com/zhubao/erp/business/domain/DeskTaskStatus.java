package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 轻任务状态。完成和作废都留下原记录，不删除。
 */
public enum DeskTaskStatus {
    OPEN("待办"),
    DONE("已完成"),
    CANCELLED("已作废");

    private final String label;

    DeskTaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DeskTaskStatus parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        for (DeskTaskStatus status : values()) {
            if (status.name().equalsIgnoreCase(text) || status.label.equals(text)) {
                return status;
            }
        }
        throw new BusinessException("未知待办状态：" + text);
    }
}
