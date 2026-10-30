-- 部分交工的数量处置。待核单独保存，不并进损耗，也不产生工资。
create table biz_quantity_disposition (
    disposition_id varchar(40) not null primary key,
    request_id varchar(80) not null,
    bag_id varchar(40) not null,
    step_id varchar(40) not null,
    handoff_qty decimal(18,3) not null,
    loss_qty decimal(18,3) not null,
    scrap_qty decimal(18,3) not null,
    returned_qty decimal(18,3) not null,
    pending_qty decimal(18,3) not null,
    unique key uk_quantity_disposition_request (request_id)
);
