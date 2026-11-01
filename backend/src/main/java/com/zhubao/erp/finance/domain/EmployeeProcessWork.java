package com.zhubao.erp.finance.domain;

import java.math.BigDecimal;

/** 来源已明确的普通作业份额。不同工序分别核对，不与末道实物数量比较。 */
public record EmployeeProcessWork(String payeeAccount, String workerName, String stepId, String processName, BigDecimal quantity) {
}
