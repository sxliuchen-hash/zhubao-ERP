package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 一次外协发外。只记录所发袋和数量，不记录客户出货、应收或员工产量，也不把未知重量写成 0。
 */
public record OutsourcingDispatch(
        String dispatchId,
        String requestId,
        String taskId,
        String partyId,
        String sourceBagId,
        String sentBagId,
        BigDecimal quantity,
        String sourceKey,
        OwnerType ownerType,
        String ownerRef,
        int version
) {
    private static final Pattern DISPATCH_ID = Pattern.compile("^OD[0-9a-f]{32}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public OutsourcingDispatch {
        if (dispatchId == null || !DISPATCH_ID.matcher(dispatchId).matches()) {
            throw new BusinessException("外协发外编号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        if (taskId == null || taskId.isBlank() || taskId.length() > 40) {
            throw new BusinessException("外协任务不存在");
        }
        taskId = taskId.trim();
        partyId = OutsourcingParty.requireId(partyId);
        sourceBagId = required(sourceBagId, "外协任务缺少所发袋");
        sentBagId = required(sentBagId, "外协任务缺少所发袋");
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("发外数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
        sourceKey = sourceKey == null ? "" : sourceKey;
        if (sourceKey.length() > 80) {
            throw new BusinessException("外协任务的原目标与所发袋不一致");
        }
        ownerType = StockMeasurement.ownerAfterOutsourcing(ownerType);
        ownerRef = required(ownerRef, "缺少物料权属");
        if (ownerRef.equals(partyId)) {
            throw new BusinessException("外协不改变物料权属");
        }
        if (version < 1) {
            throw new BusinessException("外协发外版本不正确");
        }
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > 40) {
            throw new BusinessException(message);
        }
        return text;
    }
}
