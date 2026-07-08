CREATE TABLE IF NOT EXISTS auth_user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    data_scope VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    demo_account BOOLEAN NOT NULL DEFAULT FALSE,
    password_changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_auth_user_account_username (username),
    KEY idx_auth_user_account_role (role_code),
    KEY idx_auth_user_account_enabled (enabled)
);

INSERT INTO auth_user_account (
    username, password_hash, operator, role_code, data_scope, enabled, demo_account
) VALUES
    (
        'admin',
        'pbkdf2_sha256$120000$emh1YmFvLWFkbWluLW0zLjg3$pTDPtOCRkmyk8KbKLH0EFM8ajL34RpbZY+ZKeavEuAg=',
        '系统管理员',
        'ADMIN',
        'ALL',
        TRUE,
        TRUE
    ),
    (
        'leader',
        'pbkdf2_sha256$120000$emh1YmFvLWxlYWRlci1tMy44Nw==$NcmwJomaJWrkID0fxyKkDDmacP5WXu/LA96pNayw+48=',
        '车间组长 马杰',
        'WORKSHOP_LEADER',
        'TEAM',
        TRUE,
        TRUE
    ),
    (
        'qc',
        'pbkdf2_sha256$120000$emh1YmFvLXFjLW0zLjg3$e6k1jIiuHBmR/vH44sCd/g5+yxlXDTALHKEiROZptQg=',
        '质检员 周娜',
        'QUALITY_INSPECTOR',
        'DEPARTMENT',
        TRUE,
        TRUE
    ),
    (
        'warehouse',
        'pbkdf2_sha256$120000$emh1YmFvLXdhcmVob3VzZS1tMy44Nw==$toB6gqRTD07UbM9sJOvhUEi1zxCYe8l+fPcNs6mD6l4=',
        '库管员 何琳',
        'WAREHOUSE_KEEPER',
        'DEPARTMENT',
        TRUE,
        TRUE
    ),
    (
        'operator',
        'pbkdf2_sha256$120000$emh1YmFvLW9wZXJhdG9yLW0zLjg3$ceencpGVtuUpR4fpyE3xq0pUAjiDtOBljvmugJ8XrfU=',
        '操作工 王强',
        'OPERATOR',
        'SELF',
        TRUE,
        TRUE
    )
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    operator = VALUES(operator),
    role_code = VALUES(role_code),
    data_scope = VALUES(data_scope),
    enabled = VALUES(enabled),
    demo_account = VALUES(demo_account);
