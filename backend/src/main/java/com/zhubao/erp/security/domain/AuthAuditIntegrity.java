package com.zhubao.erp.security.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AuthAuditIntegrity {

    private static final String SEPARATOR = "\u001f";

    private AuthAuditIntegrity() {
    }

    public static AuthAuditEvent seal(AuthAuditEvent event, String previousEventHash) {
        String previousHash = normalize(previousEventHash);
        return event.withIntegrity(previousHash, hash(event, previousHash));
    }

    public static boolean matches(AuthAuditEvent event) {
        return normalize(event.eventHash()).equals(hash(event, event.previousEventHash()));
    }

    public static String hash(AuthAuditEvent event, String previousEventHash) {
        String payload = String.join(SEPARATOR,
                normalize(previousEventHash),
                normalize(event.eventId()),
                normalize(event.eventType()),
                normalize(event.username()),
                normalize(event.operator()),
                normalize(event.role()),
                normalize(event.result()),
                normalize(event.reason()),
                normalize(event.occurredAt()),
                normalize(event.sessionTokenSuffix())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value;
    }
}
