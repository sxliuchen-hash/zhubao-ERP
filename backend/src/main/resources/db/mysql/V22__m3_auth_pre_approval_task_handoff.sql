ALTER TABLE auth_account_pre_approval_application
    ADD COLUMN approval_task_code VARCHAR(64) NOT NULL DEFAULT '' AFTER application_status,
    ADD KEY idx_auth_pre_approval_task_code (approval_task_code);
