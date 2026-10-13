package com.zhubao.erp.label.domain;

/** 已送入系统队列不等于已经出纸。不确定状态禁止自动重打。 */
public enum PrintJobStatus {
    PENDING,
    LEASED,
    SPOOLED,
    FAILED,
    UNCERTAIN,
    CANCELED
}
