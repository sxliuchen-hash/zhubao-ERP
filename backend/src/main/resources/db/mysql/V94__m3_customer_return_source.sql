-- 客户退货待复核来源。不保存费用金额，也不把退回写成合格成品。
create table biz_customer_return_source (
    return_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    delivery_id varchar(40) not null,
    outbound_code varchar(40) not null,
    bag_id varchar(40) not null,
    quantity decimal(18,3) not null,
    weight_value decimal(18,3) not null,
    status_code varchar(32) not null,
    adjustment_basis varchar(200) not null,
    version_no int not null,
    unique key uk_customer_return_request (request_id),
    key idx_customer_return_outbound (outbound_code)
);
