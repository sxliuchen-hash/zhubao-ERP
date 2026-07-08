ALTER TABLE auth_user_account
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE AFTER password_changed_at;
