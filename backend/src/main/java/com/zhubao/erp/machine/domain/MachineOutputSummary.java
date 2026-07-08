package com.zhubao.erp.machine.domain;

import java.math.BigDecimal;
import java.util.List;

public record MachineOutputSummary(
        int totalRecords,
        BigDecimal totalQuantity,
        String quantityUnit,
        List<MachineOutputMachineSummary> byMachine,
        List<MachineOutputWorkDateSummary> byWorkDate,
        int unmatchedMachineRecords,
        int signatureMissingRecords
) {
}
