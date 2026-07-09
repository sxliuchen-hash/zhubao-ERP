package com.zhubao.erp.gateway.error;

/**
 * Raised when a reading is rejected by replay protection (missing/expired timestamp or reused nonce).
 */
public class GatewayReplayException extends RuntimeException {

    public GatewayReplayException(String message) {
        super(message);
    }
}
