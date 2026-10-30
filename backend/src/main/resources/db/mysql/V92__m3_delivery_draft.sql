-- 独立交付草稿。只保存来源，不扣库存，也不写出库或客户账。
create table biz_delivery_draft (
    delivery_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    customer_id bigint not null,
    business_kind varchar(16) not null,
    logistics_note varchar(200) not null,
    remark varchar(500) not null,
    status_code varchar(16) not null,
    version_no int not null,
    unique key uk_delivery_draft_request (request_id),
    key idx_delivery_draft_customer (customer_id, business_kind)
);

create table biz_delivery_draft_line (
    delivery_id varchar(40) not null,
    line_no int not null,
    bag_id varchar(40) not null,
    bag_version int not null,
    business_kind varchar(16) not null,
    target_role varchar(16) not null,
    target_code varchar(90) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null,
    material_code varchar(80) not null,
    source_key varchar(80) not null,
    requirement_version varchar(40) not null,
    primary key (delivery_id, line_no),
    unique key uk_delivery_draft_line_bag (delivery_id, bag_id),
    key idx_delivery_draft_line_target (target_code)
);
