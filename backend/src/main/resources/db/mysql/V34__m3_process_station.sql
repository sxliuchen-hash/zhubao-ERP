-- 账号绑定可做工序。岗位交付记录只记哪一道已经交过，库存数量仍走台账。

ALTER TABLE auth_user_account
    ADD COLUMN allowed_processes VARCHAR(64) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS biz_process_station_handoff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_code VARCHAR(90) NOT NULL,
    process_name VARCHAR(20) NOT NULL,
    operator VARCHAR(80) NOT NULL,
    ledger_code VARCHAR(80) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_process_station_order_process (order_code, process_name)
);
