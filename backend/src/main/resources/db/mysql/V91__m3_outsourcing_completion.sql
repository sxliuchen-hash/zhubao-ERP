-- 外协完成量和费用依据。依据只有来源和数量，没有金额。
create table biz_outsourcing_completion (
    completion_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    return_id varchar(40) not null,
    task_id varchar(40) not null,
    dispatch_id varchar(40) not null,
    bag_id varchar(40) not null,
    node_id varchar(40) not null,
    target_code varchar(90) not null,
    quantity decimal(18,3) not null,
    unique key uk_outsourcing_completion_request (request_id),
    unique key uk_outsourcing_completion_return (return_id)
);

create table fin_outsourcing_fee_basis (
    basis_id varchar(40) not null primary key,
    completion_id varchar(40) not null,
    task_id varchar(40) not null,
    party_id varchar(40) not null,
    target_code varchar(90) not null,
    node_id varchar(40) not null,
    quantity decimal(18,3) not null,
    unique key uk_outsourcing_fee_basis_completion (completion_id)
);
