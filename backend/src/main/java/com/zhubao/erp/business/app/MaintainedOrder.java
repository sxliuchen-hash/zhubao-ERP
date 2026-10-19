package com.zhubao.erp.business.app;

import java.util.List;

/** 管理员看到的母单、子单或打印任务，含是否在回收站。 */
public record MaintainedOrder(
        String targetType,
        String code,
        String motherOrderCode,
        String customerName,
        String materialCode,
        String goodsName,
        String requirement,
        String dueDate,
        String quantity,
        String weight,
        String processes,
        Integer copies,
        String stationId,
        String queueId,
        boolean recycled,
        String recycleReason,
        List<String> adjustments,
        String revision
) {
}
