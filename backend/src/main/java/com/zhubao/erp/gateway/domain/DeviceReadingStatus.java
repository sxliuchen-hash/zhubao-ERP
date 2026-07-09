package com.zhubao.erp.gateway.domain;

/**
 * Normalization outcome for an ingested device reading, mirroring the register-import
 * READY / WARNING / BLOCKED convention already used across the system.
 */
public enum DeviceReadingStatus {

    /** Payload parsed cleanly and is ready to be consumed by downstream business actions. */
    READY,

    /** Payload accepted but needs human confirmation (e.g. unstable scale reading, low decode confidence). */
    WARNING,

    /** Payload could not be normalized; the raw value is retained for manual handling. */
    BLOCKED
}
