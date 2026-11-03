package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 外协费用的来源依据。数量必须有。应付款选填，不生成应付，也不形成客户账或员工工资。
 */
public record OutsourcingFeeBasis(
        String basisId,
        String completionId,
        String taskId,
        String partyId,
        String targetCode,
        String nodeId,
        BigDecimal quantity,
        BigDecimal amount
) {
    public OutsourcingFeeBasis(String basisId, String completionId, String taskId, String partyId,
                               String targetCode, String nodeId, BigDecimal quantity) {
        this(basisId, completionId, taskId, partyId, targetCode, nodeId, quantity, null);
    }
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
        if (amount != null) {
            if (amount.signum() == 0) {
                throw new BusinessException("未知金额不能写成 0");
            }
            if (amount.signum() < 0) {
                throw new BusinessException("金额不能为负");
            }
            if (amount.scale() > 2) {
                throw new BusinessException("外协应付款最多两位小数");
            }
        }
    }

    private static String required(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException("外协费用依据缺少来源");
        }
        return text;
    }
}
