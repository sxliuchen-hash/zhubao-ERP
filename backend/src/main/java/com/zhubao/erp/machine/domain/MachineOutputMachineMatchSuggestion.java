package com.zhubao.erp.machine.domain;

import java.util.List;

public record MachineOutputMachineMatchSuggestion(
        String machineNoSnapshot,
        String productCategory,
        List<Long> candidateMachineIds,
        int matchedRecords,
        int unmatchedRecords,
        int totalRecords,
        String latestWorkDate,
        String sourceRegisterType,
        String sourceMachineLineNo,
        String readiness,
        String suggestedAction
) {
}
