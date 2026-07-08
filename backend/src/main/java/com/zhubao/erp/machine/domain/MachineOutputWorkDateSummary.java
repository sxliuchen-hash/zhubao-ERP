package com.zhubao.erp.machine.domain;

import java.math.BigDecimal;

public record MachineOutputWorkDateSummary(
        String workDate,
        BigDecimal quantity,
        int records
) {
}
