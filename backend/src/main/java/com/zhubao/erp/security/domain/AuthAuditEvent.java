package com.zhubao.erp.security.domain;

public record AuthAuditEvent(
        String eventId,
        String eventType,
        String username,
        String operator,
        String role,
        String result,
        String reason,
        String occurredAt,
        String sessionTokenSuffix
) {
}
