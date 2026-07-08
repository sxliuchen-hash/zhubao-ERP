package com.zhubao.erp.machine.domain;

import java.math.BigDecimal;

public record MachineOutputMachineSummary(
        String machineNoSnapshot,
        BigDecimal quantity,
        int records
) {
}
