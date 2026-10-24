package com.zhubao.erp.business.domain;

/** 责任人勾掉待办。结果说明可空。 */
public record CompleteDeskTaskCommand(
        String requestId,
        int expectedVersion,
        String resultNote
) {
}
