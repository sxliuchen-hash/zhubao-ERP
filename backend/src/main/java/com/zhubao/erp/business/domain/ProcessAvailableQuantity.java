package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前袋在已确认路线上的可处理量、下一步和阻断原因。
 * 这是查询结果，不代表已经报工、出库或计酬。
 */
public record ProcessAvailableQuantity(
        String bagId,
        String targetCode,
        String stepId,
        String stepName,
        BigDecimal availableQuantity,
        String nextStepId,
        String nextStepName,
        List<String> blockingReasons
) {
    public ProcessAvailableQuantity {
        bagId = bagId == null ? "" : bagId;
        targetCode = targetCode == null ? "" : targetCode;
        stepId = stepId == null ? "" : stepId;
        stepName = stepName == null ? "" : stepName;
        availableQuantity = availableQuantity == null ? BigDecimal.ZERO : availableQuantity;
        nextStepId = nextStepId == null ? "" : nextStepId;
        nextStepName = nextStepName == null ? "" : nextStepName;
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }

    public boolean actionable() {
        return blockingReasons.isEmpty() && availableQuantity.signum() > 0;
    }
}
