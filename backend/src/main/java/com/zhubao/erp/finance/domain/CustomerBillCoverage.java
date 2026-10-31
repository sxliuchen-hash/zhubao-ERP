package com.zhubao.erp.finance.domain;

import com.zhubao.erp.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一张客户账单使用某条旧凭据的金额。原凭据不改，用完的额度不能再抵下一张。
 */
public record CustomerBillCoverage(
        String billId,
        String receivableCode,
        BigDecimal amount
) {
    public CustomerBillCoverage {
        if (billId == null || billId.isBlank()) {
            throw new BusinessException("客户账单编号无效");
        }
        billId = billId.trim();
        if (receivableCode == null || receivableCode.isBlank()) {
            throw new BusinessException("旧凭据编号无效");
        }
        receivableCode = receivableCode.trim();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("覆盖金额无效");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
