ALTER TABLE auth_user_account
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP NULL,
    ADD COLUMN last_login_at TIMESTAMP NULL,
    ADD COLUMN last_failed_login_at TIMESTAMP NULL;
