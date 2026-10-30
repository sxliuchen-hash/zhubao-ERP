package com.zhubao.erp.business.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次按袋份额的质量检查。多条条款不合格只记这一份数量，不按条款相加。
 */
public record PortionInspection(
        String inspectionId,
        String requestId,
        String bagId,
        String requirementVersion,
        BigDecimal pass,
        BigDecimal rework,
        BigDecimal scrap,
        BigDecimal pending,
        List<String> failedClauses
) {
    public PortionInspection {
        failedClauses = failedClauses == null ? List.of() : List.copyOf(failedClauses);
    }

    public BigDecimal counted() {
        return pass.add(rework).add(scrap).add(pending);
    }
}
