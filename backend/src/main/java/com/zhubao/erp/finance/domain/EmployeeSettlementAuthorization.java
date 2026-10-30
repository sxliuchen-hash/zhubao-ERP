package com.zhubao.erp.finance.domain;

import com.zhubao.erp.security.domain.OperatorContext;

public interface EmployeeSettlementAuthorization {

    void assertCanSettle(OperatorContext actor);
}
