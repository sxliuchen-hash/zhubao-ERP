package com.zhubao.erp.gateway.domain;

/**
 * An accountable bridge record linking a device reading to a business intent and the operator
 * who consumed it. Immutable once written.
 */
public record ReadingConsumption(
        String consumptionCode,
        String readingCode,
        String deviceCode,
        DeviceReadingType readingType,
        ReadingConsumptionIntent intent,
        String normalizedValue,
        String normalizedUnit,
        String normalizedCode,
        String businessRefType,
        String businessRefCode,
        String operator,
        String role,
        String note,
        String ledgerCode,
        String consumedAt
) {
    /** Whether this consumption drove a real business command (non-blank ledger code). */
    public boolean posted() {
        return ledgerCode != null && !ledgerCode.isBlank();
    }
}
