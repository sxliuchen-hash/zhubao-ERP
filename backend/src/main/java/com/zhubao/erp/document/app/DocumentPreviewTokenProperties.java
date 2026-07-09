package com.zhubao.erp.document.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "zhubao.documents.preview")
public class DocumentPreviewTokenProperties {

    private String secret = "zhubao-document-preview-local-secret-change-me";
    private long ttlMinutes = 15;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public String signingSecret() {
        return secret == null || secret.isBlank()
                ? "zhubao-document-preview-local-secret-change-me"
                : secret.trim();
    }

    public Duration ttl() {
        return Duration.ofMinutes(ttlMinutes <= 0 ? 15 : ttlMinutes);
    }
}
