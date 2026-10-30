-- 已确认交付的逐袋出库事实。同一袋只能确认一次，汇总单不另记库存。
create table biz_delivery_confirmed_source (
    delivery_id varchar(40) not null,
    line_no int not null,
    bag_id varchar(40) not null,
    outbound_code varchar(40) not null,
    ledger_code varchar(80) not null,
    quantity decimal(18,3) not null,
    weight_value decimal(18,3) not null,
    primary key (delivery_id, line_no),
    unique key uk_delivery_confirmed_bag (bag_id),
    unique key uk_delivery_confirmed_outbound (outbound_code)
);
