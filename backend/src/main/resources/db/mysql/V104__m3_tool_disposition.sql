-- 工具损坏和报废。只追加原因和持有人，不自动扣工资。
create table inv_tool_disposition (
    disposition_id varchar(40) not null primary key,
    loan_id varchar(40) not null,
    holder_id varchar(40) not null,
    kind varchar(16) not null,
    quantity decimal(18,3) not null,
    reason varchar(80) not null
);
