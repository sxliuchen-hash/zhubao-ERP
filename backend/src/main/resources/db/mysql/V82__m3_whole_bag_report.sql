-- 正常整袋报工批次和节点份额。进度百分比可空，不代替交工，也不产生工资。
create table biz_whole_bag_report (
    batch_code varchar(40) not null primary key,
    request_id varchar(64) not null,
    bag_id varchar(40) not null,
    bag_version int not null,
    target_code varchar(90) not null,
    step_id varchar(40) not null,
    quantity decimal(18,3) not null,
    attachment_code varchar(80) not null,
    operator_name varchar(64) not null,
    progress_percent int null,
    created_at varchar(30) not null,
    unique key uk_whole_bag_request (request_id)
);

create table biz_node_portion (
    bag_id varchar(40) not null,
    step_id varchar(40) not null,
    arrived decimal(18,3) not null,
    handled decimal(18,3) not null,
    primary key (bag_id, step_id)
);
