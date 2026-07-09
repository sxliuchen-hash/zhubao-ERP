ALTER TABLE auth_user_account
    ADD COLUMN temporary_password_consumed BOOLEAN NOT NULL DEFAULT FALSE AFTER password_change_required;
