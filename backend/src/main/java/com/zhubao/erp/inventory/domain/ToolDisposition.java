package com.zhubao.erp.inventory.domain;

import java.math.BigDecimal;

/** 工具损坏或报废。留下原因和当时持有人，不自动扣工资。 */
public record ToolDisposition(
        String dispositionId,
        String loanId,
        String holderId,
        String kind,
        BigDecimal quantity,
        String reason
) {
    public static final String DAMAGE = "DAMAGE";
    public static final String SCRAP = "SCRAP";
}
