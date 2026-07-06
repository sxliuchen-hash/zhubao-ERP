-- M2 schema draft for configuration change control.
-- These tables are required before configuration editing can be enabled in production.

create table cfg_change_request (
    id bigint primary key auto_increment,
    request_code varchar(80) not null unique,
    baseline_code varchar(80) not null,
    target_key varchar(160) not null,
    target_name varchar(160) not null,
    change_type varchar(80) not null,
    before_value varchar(500) null,
    after_value varchar(500) not null,
    change_reason varchar(1000) not null,
    applicant_name varchar(80) not null,
    approver_name varchar(80) null,
    request_status varchar(40) not null,
    risk_level varchar(40) not null,
    submitted_at datetime(3) not null,
    decided_at datetime(3) null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    index idx_cfg_change_target (baseline_code, target_key),
    index idx_cfg_change_status (baseline_code, request_status, risk_level)
);

create table cfg_change_approval_step (
    id bigint primary key auto_increment,
    request_code varchar(80) not null,
    step_order int not null,
    approver_role varchar(80) not null,
    approver_name varchar(80) null,
    step_status varchar(40) not null,
    decision_comment varchar(500) null,
    decided_at datetime(3) null,
    unique key uk_cfg_change_approval_step (request_code, step_order),
    index idx_cfg_change_approval_status (request_code, step_status)
);

create table cfg_audit_event (
    id bigint primary key auto_increment,
    event_id varchar(80) not null unique,
    baseline_code varchar(80) not null,
    target_key varchar(160) not null,
    operation varchar(80) not null,
    operator_name varchar(80) not null,
    occurred_at datetime(3) not null,
    event_result varchar(40) not null,
    evidence varchar(500) null,
    trace_id varchar(120) not null,
    index idx_cfg_audit_target (baseline_code, target_key, occurred_at),
    index idx_cfg_audit_trace (trace_id)
);
