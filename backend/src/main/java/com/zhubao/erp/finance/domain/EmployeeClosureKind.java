package com.zhubao.erp.finance.domain;

/**
 * 员工核算所看到的目标闭合情况。代报、返工和全损的计酬仍待核，不能当成已经闭合的正常完工。
 */
public enum EmployeeClosureKind {
    NOT_CLOSED,
    CLOSED_NORMAL,
    TOTAL_LOSS,
    REWORK_OPEN
}
