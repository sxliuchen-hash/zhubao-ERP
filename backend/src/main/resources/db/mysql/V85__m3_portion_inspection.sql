-- 按袋份额的质量检查。条款只作说明，数量不按条款重复计算。
create table biz_portion_inspection (
    inspection_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    bag_id varchar(40) not null,
    requirement_version varchar(40) not null,
    pass_qty decimal(18,3) not null,
    rework_qty decimal(18,3) not null,
    scrap_qty decimal(18,3) not null,
    pending_qty decimal(18,3) not null,
    failed_clauses varchar(400) not null,
    unique key uk_portion_inspection_request (request_id)
);
