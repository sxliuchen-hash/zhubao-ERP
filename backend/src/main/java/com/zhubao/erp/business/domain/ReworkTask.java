package com.zhubao.erp.business.domain;

import java.math.BigDecimal;

/**
 * 从原不合格份额开出的返工。仍挂在原加工目标上，不新建母单，也不决定额外计酬。
 */
public record ReworkTask(
        String reworkId,
        String requestId,
        String sourceBagId,
        String reworkBagId,
        String targetCode,
        String requirementVersion,
        int round,
        BigDecimal quantity
) {
}
