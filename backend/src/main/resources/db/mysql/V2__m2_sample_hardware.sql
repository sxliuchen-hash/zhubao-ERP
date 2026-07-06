-- M2 schema draft for sample management and hardware integration baseline.
-- These tables are not wired yet; they define the persistence target for M3.

create table sys_sample_scenario (
    id bigint primary key auto_increment,
    scenario_code varchar(80) not null unique,
    scenario_name varchar(120) not null,
    owner_name varchar(80) not null,
    purpose varchar(500) not null,
    status varchar(30) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    index idx_sample_scenario_status (status)
);

create table sys_sample_scenario_step (
    id bigint primary key auto_increment,
    scenario_code varchar(80) not null,
    step_order int not null,
    step_name varchar(120) not null,
    unique key uk_sample_step_order (scenario_code, step_order),
    index idx_sample_step_scenario (scenario_code)
);

create table sys_sample_scenario_evidence (
    id bigint primary key auto_increment,
    scenario_code varchar(80) not null,
    evidence_name varchar(120) not null,
    evidence_rule varchar(500) null,
    index idx_sample_evidence_scenario (scenario_code)
);

create table hw_device (
    id bigint primary key auto_increment,
    device_code varchar(80) not null unique,
    device_name varchar(120) not null,
    scene varchar(200) not null,
    protocol varchar(200) not null,
    status varchar(30) not null,
    fallback varchar(500) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    index idx_hw_device_status (status)
);

create table hw_integration_task (
    id bigint primary key auto_increment,
    task_code varchar(80) not null unique,
    title varchar(160) not null,
    target varchar(200) not null,
    health int not null,
    last_run varchar(160) not null,
    next_action varchar(500) not null,
    owner_name varchar(80) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    index idx_hw_task_health (health),
    index idx_hw_task_owner (owner_name)
);
