package com.zhubao.erp.business.domain;

/**
 * 母单拆分决定。KEEP 表示不拆，仍用母单号打一张贴纸；SPLIT 表示已拆出子单。
 */
public record MotherSplitState(
        String motherOrderCode,
        String decision,
        String verificationCode,
        String operator,
        String createdAt
) {
    public static final String KEEP = "KEEP";
    public static final String SPLIT = "SPLIT";
}
