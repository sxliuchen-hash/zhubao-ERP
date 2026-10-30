package com.zhubao.erp.business.domain;

import com.zhubao.erp.common.error.BusinessException;

/**
 * 外协交接里的对方身份。外协方不是客户，也不是本厂员工。
 */
public enum OutsourcingCounterpart {
    OUTSOURCING_PARTY,
    CUSTOMER,
    EMPLOYEE;

    public void assertParty() {
        if (this != OUTSOURCING_PARTY) {
            throw new BusinessException("外协方不能登记成客户或员工");
        }
    }
}
