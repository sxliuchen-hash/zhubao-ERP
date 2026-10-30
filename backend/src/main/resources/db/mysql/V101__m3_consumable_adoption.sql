-- 旧耗材结存接续。待核记录不生成可改余额。
create table inv_consumable_adoption (
    adoption_id varchar(40) not null primary key,
    old_item_code varchar(40) not null unique,
    status varchar(16) not null,
    reason varchar(80) not null,
    factory_item_id varchar(40) not null,
    receipt_id varchar(40) not null,
    quantity decimal(18,3) null,
    amount_state varchar(16) not null,
    amount_value decimal(18,2) null,
    spec_code varchar(40) not null,
    warehouse_code varchar(40) not null
);
