package com.zhubao.erp.goldwork.domain;

/** 金工单自身状态。完结和收费尚未核定，这里不提供已完成。 */
public enum GoldworkOrderStatus {
    OPEN("进行中");

    private final String label;

    GoldworkOrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
