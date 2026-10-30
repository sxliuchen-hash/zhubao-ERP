-- 返工份额。仍使用原加工目标的单号，不另建母单。
create table biz_rework_task (
    rework_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    source_bag_id varchar(40) not null,
    rework_bag_id varchar(40) not null,
    target_code varchar(90) not null,
    requirement_version varchar(40) not null,
    round_no int not null,
    quantity decimal(18,3) not null,
    unique key uk_rework_task_request (request_id)
);
