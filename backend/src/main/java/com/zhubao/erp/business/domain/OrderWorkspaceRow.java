package com.zhubao.erp.business.domain;

/**
 * 订单中心的一行当前目标。不包含金额、附件地址或库存写入参数。
 * null 表示未知，不能当成 0。
 */
public record OrderWorkspaceRow(
        String rowKey,
        String objectType,
        String code,
        String motherOrderCode,
        String workOrderCode,
        String executionRoundCode,
        String currentProcess,
        Long customerId,
        String customerName,
        String goodsName,
        String materialCode,
        String materialName,
        String quantity,
        String quantityUnit,
        String weight,
        String weightUnit,
        String dueDate,
        String businessStatus,
        String facet,
        String facetNote,
        Integer processPercent,
        Integer processProgressVersion,
        Boolean processFormallyDelivered,
        String operatorName,
        String updatedAt
) {
}
