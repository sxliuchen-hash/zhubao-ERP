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
        String sessionTokenSuffix,
        String previousEventHash,
        String eventHash
) {
    public AuthAuditEvent(
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
        this(eventId, eventType, username, operator, role, result, reason, occurredAt, sessionTokenSuffix, "", "");
    }

    public AuthAuditEvent withIntegrity(String previousEventHash, String eventHash) {
        return new AuthAuditEvent(
                eventId,
                eventType,
                username,
                operator,
                role,
                result,
                reason,
                occurredAt,
                sessionTokenSuffix,
                previousEventHash,
                eventHash
        );
    }
}
