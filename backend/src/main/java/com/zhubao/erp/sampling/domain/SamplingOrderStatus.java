package com.zhubao.erp.sampling.domain;

/**
 * 打样单自身状态。建单后为进行中，不因缺少普通加工母单而无法存在。
 * 完结资格尚未核定，这里不提供已完成。
 */
public enum SamplingOrderStatus {
    OPEN("进行中");

    private final String label;

    SamplingOrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
