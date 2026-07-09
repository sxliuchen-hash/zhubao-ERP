-- M3.123 Hardware gateway reading consumption contract.
-- An accountable bridge record linking a device reading to a business intent and the operator
-- who consumed it. Consumption does not itself write the business ledger; it audits who turned a
-- raw device reading into a business signal so telemetry and business actions can be reconciled.

CREATE TABLE IF NOT EXISTS hw_gateway_reading_consumption (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consumption_code VARCHAR(96) NOT NULL,
    reading_code VARCHAR(80) NOT NULL,
    device_code VARCHAR(60) NOT NULL,
    reading_type VARCHAR(40) NOT NULL,
    intent VARCHAR(40) NOT NULL,
    normalized_value VARCHAR(120),
    normalized_unit VARCHAR(20),
    normalized_code VARCHAR(120),
    business_ref_type VARCHAR(64),
    business_ref_code VARCHAR(120),
    operator VARCHAR(80) NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    note VARCHAR(255),
    consumed_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gateway_consumption_code (consumption_code),
    UNIQUE KEY uk_gateway_consumption_reading (reading_code),
    KEY idx_gateway_consumption_device (device_code),
    KEY idx_gateway_consumption_intent (intent)
);
