package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 外协收回后确认的作业完成量。只记实收份额，未加工退回不在这里。
 */
public record OutsourcingCompletion(
        String completionId,
        String requestId,
        String returnId,
        String taskId,
        String dispatchId,
        String bagId,
        String nodeId,
        String targetCode,
        BigDecimal quantity
) {
    private static final Pattern COMPLETION_ID = Pattern.compile("^OC[0-9a-f]{32}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public OutsourcingCompletion {
        if (completionId == null || !COMPLETION_ID.matcher(completionId).matches()) {
            throw new BusinessException("外协完成编号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        returnId = required(returnId, "外协收回不存在", 40);
        taskId = required(taskId, "外协任务不存在", 40);
        dispatchId = required(dispatchId, "外协发外不存在", 40);
        bagId = required(bagId, "外协任务缺少所发袋", 40);
        nodeId = required(nodeId, "外协任务缺少原节点", 40);
        targetCode = required(targetCode, "外协任务缺少原目标", 90);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("外协完成数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
    }

    private static String required(String value, String message, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(message);
        }
        return text;
    }
}
