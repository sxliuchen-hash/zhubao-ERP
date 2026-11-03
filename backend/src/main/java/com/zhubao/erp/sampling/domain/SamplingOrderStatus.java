package com.zhubao.erp.sampling.domain;

/**
 * 打样单自身状态。建单后为进行中。已报数量交完且每次样费已确认或免收后可以完结。
 */
public enum SamplingOrderStatus {
    OPEN("进行中"),
    COMPLETED("已完成");

    private final String label;

    SamplingOrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
