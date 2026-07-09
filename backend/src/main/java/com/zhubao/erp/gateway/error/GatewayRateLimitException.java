package com.zhubao.erp.gateway.error;

/**
 * Raised when a device exceeds its ingestion quota within the rolling rate-limit window.
 */
public class GatewayRateLimitException extends RuntimeException {

    public GatewayRateLimitException(String message) {
        super(message);
    }
}
