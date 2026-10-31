package com.zhubao.erp.business.domain;

/**
 * 下游事实是否已经查到。查不到不能写成没有，也不是 0。
 */
public enum DownstreamFact {
    CONFIRMED("已确认"),
    ABSENT("无"),
    UNDECIDED("待核");

    private final String label;

    DownstreamFact(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
