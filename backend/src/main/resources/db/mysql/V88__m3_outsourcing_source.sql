-- 外协方稳定身份和任务来源。外协方不是客户或员工，任务保留当时名称和原权属。
create table biz_outsourcing_party (
    party_id varchar(40) not null primary key,
    party_name varchar(80) not null,
    counterpart varchar(32) not null,
    version_no int not null
);

create table biz_outsourcing_task (
    task_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    party_id varchar(40) not null,
    party_name_snapshot varchar(80) not null,
    business_kind varchar(16) not null,
    target_code varchar(90) not null,
    node_id varchar(40) not null,
    bag_id varchar(40) not null,
    owner_type varchar(16) not null,
    owner_ref varchar(40) not null,
    version_no int not null,
    unique key uk_outsourcing_task_request (request_id),
    key idx_outsourcing_task_target (target_code),
    key idx_outsourcing_task_bag (bag_id)
);
