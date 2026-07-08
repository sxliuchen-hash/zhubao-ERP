package com.zhubao.erp.hardware.domain;

import java.util.List;

public record HardwarePocEvidenceSummary(
        String pocCode,
        String scene,
        String result,
        String documentCode,
        int attachmentCount,
        int missingEvidence,
        List<String> attachmentCodes,
        String latestUploadedAt,
        String status,
        String nextAction,
        String tone
) {
}
