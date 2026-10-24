package com.zhubao.erp.business.domain;

/** 创建人作废尚未完成的待办。原因必填，记录保留。 */
public record CancelDeskTaskCommand(
        String requestId,
        int expectedVersion,
        String reason
) {
}
