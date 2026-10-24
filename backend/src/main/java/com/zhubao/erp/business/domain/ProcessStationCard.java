package com.zhubao.erp.business.domain;

/**
 * 当前账号在这张单上可以看见的一道工序。
 */
public record ProcessStationCard(
        String processName,
        String requirement,
        String dueDate,
        String previousProcessName,
        boolean previousSubmitted,
        boolean submitted,
        java.math.BigDecimal totalQuantity,
        java.math.BigDecimal deliveredQuantity,
        java.math.BigDecimal availableQuantity,
        boolean finalProcess,
        java.util.List<ProcessBatchHandoff> batches
) {
    public ProcessStationCard(String processName, String requirement, String dueDate, String previousProcessName,
                              boolean previousSubmitted, boolean submitted) {
        this(processName, requirement, dueDate, previousProcessName, previousSubmitted, submitted,
                null, null, null, false, java.util.List.of());
    }
}
