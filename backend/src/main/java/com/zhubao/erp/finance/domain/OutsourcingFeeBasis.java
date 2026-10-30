package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 外协费用的来源依据。只引用已确认的作业数量，不包含金额，也不形成客户账或员工工资。
 */
public record OutsourcingFeeBasis(
        String basisId,
        String completionId,
        String taskId,
        String partyId,
        String targetCode,
        String nodeId,
        BigDecimal quantity
) {
    private static final Pattern BASIS_ID = Pattern.compile("^FB[0-9a-f]{32}$");

    public OutsourcingFeeBasis {
        if (basisId == null || !BASIS_ID.matcher(basisId).matches()) {
            throw new BusinessException("外协费用依据编号无效");
        }
        completionId = required(completionId, 40);
        taskId = required(taskId, 40);
        partyId = com.zhubao.erp.business.domain.OutsourcingParty.requireId(partyId);
        targetCode = required(targetCode, 90);
        nodeId = required(nodeId, 40);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("外协完成数量必须为正");
        }
        quantity = StockMeasurement.processingScale(quantity);
    }

    private static String required(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException("外协费用依据缺少来源");
        }
        return text;
    }
}
