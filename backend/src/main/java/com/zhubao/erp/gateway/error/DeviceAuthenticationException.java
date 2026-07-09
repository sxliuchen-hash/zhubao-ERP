package com.zhubao.erp.gateway.error;

/**
 * Raised when a device fails to authenticate against the ingestion gateway
 * (unknown device code, disabled device, or invalid device key).
 */
public class DeviceAuthenticationException extends RuntimeException {

    public DeviceAuthenticationException(String message) {
        super(message);
    }
}
