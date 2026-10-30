package com.zhubao.erp.business.domain;

/**
 * 同一业务种类里的目标角色。子单仍然属于普通加工，不是另一类业务。
 */
public enum BusinessTargetRole {
    MOTHER,
    WORK_ORDER,
    DOCUMENT
}
