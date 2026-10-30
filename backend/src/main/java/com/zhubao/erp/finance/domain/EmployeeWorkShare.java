package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一名计酬人的实作份额。代报为真时不能直接入账，因为代报计酬尚未核定。
 */
public record EmployeeWorkShare(
        String payeeAccount,
        String processName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        boolean proxy
) {
    public EmployeeWorkShare {
        payeeAccount = required(payeeAccount, "计酬账号不能为空");
        processName = required(processName, "工序不能为空");
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("计酬数量必须为正");
        }
        quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new BusinessException("工价无效");
        }
        unitPrice = unitPrice.setScale(4, RoundingMode.HALF_UP);
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(message);
        }
        return text;
    }
}
