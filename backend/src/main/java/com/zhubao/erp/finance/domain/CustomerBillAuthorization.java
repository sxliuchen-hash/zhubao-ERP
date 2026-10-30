package com.zhubao.erp.finance.domain;

import com.zhubao.erp.security.domain.OperatorContext;

/**
 * 客户加工费入账授权。不要求工资权限。
 */
public interface CustomerBillAuthorization {

    void assertCanBill(OperatorContext actor, long customerId);
}
