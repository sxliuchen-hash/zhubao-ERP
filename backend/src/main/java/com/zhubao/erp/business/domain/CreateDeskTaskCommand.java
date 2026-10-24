package com.zhubao.erp.business.domain;

/**
 * 新建轻任务。客户和母单都可以不填；填写时由服务核对范围，不由此处信任名称。
 */
public record CreateDeskTaskCommand(
        String kind,
        String title,
        String note,
        Long customerId,
        String motherOrderCode,
        String assigneeUsername,
        String dueDate,
        String requestId
) {
}
