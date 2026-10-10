package com.zhubao.erp.document.app;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "zhubao.documents.preview")
public class DocumentPreviewTokenProperties {

    private static final String LOCAL_SECRET = "zhubao-document-preview-local-secret-change-me";
    private String secret = LOCAL_SECRET;
    private long ttlMinutes = 15;
    private boolean requireExplicitSecret;

    public boolean isRequireExplicitSecret() { return requireExplicitSecret; }

    public void setRequireExplicitSecret(boolean requireExplicitSecret) { this.requireExplicitSecret = requireExplicitSecret; }

    /** 生产启动时拒绝本地默认密钥，避免所有安装共享预览授权签名。 */
    @PostConstruct
    public void validate() {
        if (requireExplicitSecret && (secret == null || secret.trim().length() < 32 || LOCAL_SECRET.equals(secret.trim()))) {
            throw new IllegalStateException("生产附件预览必须显式配置至少 32 字符的独立签名密钥");
        }
    }

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
                ? LOCAL_SECRET
                : secret.trim();
    }

    public Duration ttl() {
        return Duration.ofMinutes(ttlMinutes <= 0 ? 15 : ttlMinutes);
    }
}
