package com.zhubao.erp.sampling.domain;

import java.math.BigDecimal;

/** 打样报工查询。没有母单号，也不表示普通工序已经完成。 */
public record SamplingOperationView(
        String reportId,
        String samplingCode,
        String stepId,
        String stepName,
        String versionKind,
        int versionNo,
        String makingChoice,
        String makingChoiceLabel,
        BigDecimal quantity,
        String unit,
        String attachmentCode,
        String workerName,
        String reportedOn
) {
}
