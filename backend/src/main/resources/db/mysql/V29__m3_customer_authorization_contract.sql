-- M3.130 Customer authorization table.
-- Replaces the loose customer_scope text field on the account with a maintainable authorization
-- table. A CUSTOMER_RANGE user's effective customer scope becomes the set of customer codes
-- granted here; when a user has no rows, the account's customer_scope field is used as fallback.

CREATE TABLE IF NOT EXISTS sys_customer_authorization (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    customer_code VARCHAR(120) NOT NULL,
    customer_name VARCHAR(120),
    granted_by VARCHAR(80),
    granted_at VARCHAR(40),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_customer_auth (username, customer_code),
    KEY idx_customer_auth_username (username)
);
