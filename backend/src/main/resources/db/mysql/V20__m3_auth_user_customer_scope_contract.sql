ALTER TABLE auth_user_account
    ADD COLUMN customer_scope VARCHAR(512) NOT NULL DEFAULT '' AFTER data_scope;
