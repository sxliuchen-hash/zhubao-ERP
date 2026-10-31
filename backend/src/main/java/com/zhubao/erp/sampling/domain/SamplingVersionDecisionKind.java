package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.common.error.BusinessException;

/** 客户对某一个版本的决定。决定只追加一次，不改版本正文。 */
public enum SamplingVersionDecisionKind {
    CONFIRMED("已确认"),
    REJECTED("已驳回");

    private final String label;

    SamplingVersionDecisionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
