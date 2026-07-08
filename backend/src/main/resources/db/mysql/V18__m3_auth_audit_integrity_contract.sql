ALTER TABLE auth_audit_event
    ADD COLUMN previous_event_hash VARCHAR(64) NOT NULL DEFAULT '' AFTER session_token_suffix,
    ADD COLUMN event_hash VARCHAR(64) NOT NULL DEFAULT '' AFTER previous_event_hash;

CREATE INDEX idx_auth_audit_event_hash ON auth_audit_event (event_hash);
