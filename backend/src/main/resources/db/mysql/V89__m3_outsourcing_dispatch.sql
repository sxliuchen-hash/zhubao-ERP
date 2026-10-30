-- 外协发外明细。保管转到外协方，权属和来源仍记在原任务与原袋上。
create table biz_outsourcing_dispatch (
    dispatch_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    task_id varchar(40) not null,
    party_id varchar(40) not null,
    source_bag_id varchar(40) not null,
    sent_bag_id varchar(40) not null,
    quantity decimal(18,3) not null,
    source_key varchar(80) not null,
    owner_type varchar(16) not null,
    owner_ref varchar(40) not null,
    version_no int not null,
    unique key uk_outsourcing_dispatch_request (request_id),
    unique key uk_outsourcing_dispatch_sent_bag (sent_bag_id),
    key idx_outsourcing_dispatch_task (task_id)
);
