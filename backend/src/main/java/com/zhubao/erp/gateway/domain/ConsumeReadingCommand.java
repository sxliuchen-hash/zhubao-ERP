package com.zhubao.erp.gateway.domain;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Human command to consume a device reading into a business intent. Guarded by RBAC and the
 * operator context is resolved from the request (not carried in the body).
 *
 * <p>The optional {@code posting*} fields let a {@code WEIGH_HANDOVER} or {@code SCAN_TO_REPORT}
 * consumption drive a real production report (WIP → SEMI_FINISHED) through
 * {@code BusinessOperationService}. When they are complete the reading truly writes the stock
 * ledger; when omitted the consumption degrades to a pure audited bridge record (existing
 * behavior). This keeps the low-cost hardware pipeline half-automatic and reversible.</p>
 */
public record ConsumeReadingCommand(
        @NotBlank String readingCode,
        @NotBlank String intent,
        String businessRefType,
        String businessRefCode,
        String note,
        Long postingCustomerId,
        Long postingOwnerCustomerId,
        String postingMotherOrderCode,
        String postingWorkOrderCode,
        String postingMaterialCode,
        String postingFromWarehouseCode,
        String postingToWarehouseCode,
        BigDecimal postingQuantity,
        String postingProcess
) {
    /** Whether the caller supplied enough context to drive a real production report. */
    public boolean hasProductionPosting() {
        return postingCustomerId != null
                && isPresent(postingMotherOrderCode)
                && isPresent(postingMaterialCode)
                && isPresent(postingFromWarehouseCode)
                && isPresent(postingToWarehouseCode)
                && postingQuantity != null
                && postingQuantity.signum() > 0;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
