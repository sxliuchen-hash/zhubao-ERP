package com.zhubao.erp.finance.domain;

/**
 * 有限计费方式。没有脚本或任意公式。
 */
public enum CustomerChargeMethod {
    QUANTITY,
    BASE_MINUS_QUANTITY,
    AGREED_AMOUNT,
    ADJUSTMENT
}
