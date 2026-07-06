-- M2 schema draft for configuration baseline management.
-- These tables are the persistence target for /api/configuration/baseline.
-- Editing workflows, approval records, and audit logs should be added before production writes.

create table cfg_baseline_snapshot (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null unique,
    baseline_name varchar(120) not null,
    freeze_stage varchar(80) not null,
    effective_date date not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
);

create table cfg_system_parameter (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    param_key varchar(120) not null,
    param_value varchar(200) not null,
    category varchar(60) not null,
    config_level varchar(20) not null,
    description varchar(500) not null,
    change_control varchar(200) not null,
    effective_scope varchar(160) not null,
    unique key uk_cfg_param (baseline_code, param_key),
    index idx_cfg_param_level (baseline_code, config_level)
);

create table cfg_code_rule (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    object_type varchar(80) not null,
    code_format varchar(200) not null,
    example_code varchar(120) not null,
    uniqueness_rule varchar(200) not null,
    change_policy varchar(200) not null,
    unique key uk_cfg_code_rule (baseline_code, object_type)
);

create table cfg_role_permission_baseline (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    role_name varchar(80) not null,
    data_scope varchar(120) not null,
    permissions_json json not null,
    sensitive_actions_json json not null,
    unique key uk_cfg_role_permission (baseline_code, role_name)
);

create table cfg_process_template (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    template_code varchar(80) not null,
    template_name varchar(120) not null,
    material_scope varchar(240) not null,
    loss_rule varchar(500) not null,
    wage_rule varchar(500) not null,
    quality_rule varchar(500) not null,
    snapshot_fields varchar(500) not null,
    unique key uk_cfg_process_template (baseline_code, template_code)
);

create table cfg_process_template_step (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    template_code varchar(80) not null,
    step_order int not null,
    step_name varchar(120) not null,
    unique key uk_cfg_process_step (baseline_code, template_code, step_order),
    index idx_cfg_process_step_template (baseline_code, template_code)
);

create table cfg_warehouse_state (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    state_code varchar(80) not null,
    state_name varchar(120) not null,
    owner_boundary varchar(80) not null,
    stock_status varchar(80) not null,
    transition_rule varchar(300) not null,
    negative_allowed tinyint(1) not null default 0,
    unique key uk_cfg_warehouse_state (baseline_code, state_code)
);

create table cfg_video_parameter (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    param_key varchar(120) not null,
    param_value varchar(200) not null,
    scene varchar(240) not null,
    fallback varchar(300) not null,
    retention varchar(160) not null,
    change_control varchar(200) not null,
    unique key uk_cfg_video_param (baseline_code, param_key)
);

create table cfg_component_baseline (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    component_name varchar(120) not null,
    component_version varchar(80) not null,
    license_name varchar(120) not null,
    purpose varchar(240) not null,
    source varchar(240) not null,
    risk varchar(500) not null,
    unique key uk_cfg_component (baseline_code, component_name)
);

create table cfg_backup_policy (
    id bigint primary key auto_increment,
    baseline_code varchar(80) not null,
    target_name varchar(120) not null,
    strategy varchar(300) not null,
    rpo varchar(120) not null,
    rto varchar(120) not null,
    owner_name varchar(80) not null,
    unique key uk_cfg_backup_policy (baseline_code, target_name)
);
