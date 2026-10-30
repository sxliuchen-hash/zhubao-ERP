package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.util.regex.Pattern;

/**
 * 一张外协任务的来源。保留建单时的外协方名称和原物料权属，不记录发外数量、费用或工资。
 */
public record OutsourcingTask(
        String taskId,
        String requestId,
        String partyId,
        String partyNameSnapshot,
        BusinessKind businessKind,
        String targetCode,
        String nodeId,
        String bagId,
        OwnerType ownerType,
        String ownerRef,
        int version
) {
    private static final Pattern TASK_ID = Pattern.compile("^OT[0-9a-f]{32}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public OutsourcingTask {
        if (taskId == null || !TASK_ID.matcher(taskId).matches()) {
            throw new BusinessException("外协任务编号无效");
        }
        String request = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID.matcher(request).matches()) {
            throw new BusinessException("请求编号无效");
        }
        requestId = request;
        partyId = OutsourcingParty.requireId(partyId);
        partyNameSnapshot = OutsourcingParty.requireName(partyNameSnapshot);
        if (businessKind == null) {
            throw new BusinessException("业务引用缺少种类或角色");
        }
        BusinessTargetReference parsed = BusinessTargetReference.fromCode(targetCode);
        if (parsed.kind() != businessKind) {
            throw new BusinessException("业务引用种类与单号不一致");
        }
        targetCode = parsed.code();
        if (nodeId == null || nodeId.isBlank()) {
            throw new BusinessException("外协任务缺少原节点");
        }
        ProcessStep step = ProcessCatalog.require(nodeId.trim());
        if (step.kind() != businessKind) {
            throw new BusinessException("工序不属于该业务");
        }
        nodeId = step.id();
        bagId = required(bagId, "外协任务缺少所发袋", 40);
        ownerType = StockMeasurement.ownerAfterOutsourcing(ownerType);
        ownerRef = required(ownerRef, "缺少物料权属", 40);
        if (ownerRef.equals(partyId)) {
            throw new BusinessException("外协不改变物料权属");
        }
        if (version < 1) {
            throw new BusinessException("外协任务版本不正确");
        }
    }

    private static String required(String value, String message, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(message);
        }
        return text;
    }
}
