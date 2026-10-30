-- 公司珠料和配件入库。金额未知留空，不写成 0；重量未知也留空。
create table inv_company_material_receipt (
    receipt_id varchar(40) not null primary key,
    item_id varchar(40) not null,
    batch_code varchar(40) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null,
    warehouse_code varchar(40) not null,
    source_kind varchar(32) not null,
    supplier_name varchar(80) not null,
    amount_state varchar(16) not null,
    amount_value decimal(18,2) null,
    payable_code varchar(40) not null,
    covered_payable_code varchar(40) not null,
    version_no int not null
);

create table inv_company_material_balance (
    item_id varchar(40) not null,
    batch_code varchar(40) not null,
    warehouse_code varchar(40) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null,
    primary key (item_id, batch_code, warehouse_code)
);
