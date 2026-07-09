-- M3.122 Hardware device gateway & telemetry ingestion contract.
-- Registers low-cost devices (authenticated by a hashed shared key) and stores the
-- normalized readings they push into the ingestion gateway.

CREATE TABLE IF NOT EXISTS hw_gateway_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_code VARCHAR(60) NOT NULL,
    device_name VARCHAR(120) NOT NULL,
    accepted_types VARCHAR(255) NOT NULL,
    device_key_hash VARCHAR(255) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    location VARCHAR(120),
    fallback_note VARCHAR(255),
    created_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gateway_device_code (device_code)
);

CREATE TABLE IF NOT EXISTS hw_gateway_reading (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reading_code VARCHAR(80) NOT NULL,
    device_code VARCHAR(60) NOT NULL,
    reading_type VARCHAR(40) NOT NULL,
    raw_payload TEXT NOT NULL,
    normalized_value VARCHAR(120),
    normalized_unit VARCHAR(20),
    normalized_code VARCHAR(120),
    reading_status VARCHAR(20) NOT NULL,
    message VARCHAR(255),
    business_ref_type VARCHAR(64),
    business_ref_code VARCHAR(120),
    captured_at VARCHAR(40),
    received_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gateway_reading_code (reading_code),
    KEY idx_gateway_reading_device (device_code),
    KEY idx_gateway_reading_status (reading_status),
    KEY idx_gateway_reading_received (received_at)
);
