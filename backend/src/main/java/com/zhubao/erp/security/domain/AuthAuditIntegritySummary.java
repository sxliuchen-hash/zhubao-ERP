package com.zhubao.erp.security.domain;

public record AuthAuditIntegritySummary(
        int checkedEvents,
        boolean verified,
        String latestEventId,
        String latestEventHash,
        String brokenEventId,
        String message
) {
}
