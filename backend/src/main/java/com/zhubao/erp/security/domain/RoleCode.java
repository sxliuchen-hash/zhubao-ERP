package com.zhubao.erp.security.domain;

public enum RoleCode {
    ADMIN,
    GENERAL_MANAGER,
    BUSINESS_MANAGER,
    SALES,
    RECEIVER,
    WORKSHOP_LEADER,
    OPERATOR,
    QUALITY_INSPECTOR,
    WAREHOUSE_KEEPER,
    FINANCE_STATISTICIAN,
    /** 客户手机只读入口。没有内部岗位权限，也不能携带全部数据范围或顶岗。 */
    CUSTOMER_PORTAL
}
