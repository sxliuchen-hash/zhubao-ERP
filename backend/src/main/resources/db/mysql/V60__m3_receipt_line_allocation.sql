-- 新收货逐行保存材质来源；旧单据仍由原单材质和旧库存流水合成一条只读明细。
create table biz_receipt_line (
    mother_order_code varchar(80) not null,
    line_no int not null,
    material_code varchar(80) not null,
    material_name_snapshot varchar(120) not null,
    goods_name_snapshot varchar(160) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(20) not null,
    weight decimal(18,3) not null,
    ledger_code varchar(80) not null,
    primary key (mother_order_code, line_no),
    unique key uk_receipt_line_ledger (ledger_code),
    key idx_receipt_line_material (material_code),
    constraint fk_receipt_line_mother foreign key (mother_order_code) references biz_mother_order(code)
);

create table biz_child_line_allocation (
    child_order_code varchar(90) not null,
    mother_order_code varchar(80) not null,
    source_line_no int not null,
    material_code varchar(80) not null,
    quantity decimal(18,3) not null,
    weight decimal(18,3) not null,
    ledger_code varchar(80) not null,
    primary key (child_order_code, source_line_no),
    unique key uk_child_line_ledger (ledger_code),
    key idx_child_allocation_mother_line (mother_order_code, source_line_no),
    constraint fk_child_allocation_child foreign key (child_order_code) references biz_work_order(code),
    constraint fk_child_allocation_source foreign key (mother_order_code, source_line_no)
        references biz_receipt_line(mother_order_code, line_no)
);
