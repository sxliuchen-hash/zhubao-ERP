-- 账单覆盖按目标和单位分别保留，历史账单不补造不存在的来源。
create table fin_customer_bill_source (
    bill_id varchar(40) not null,
    source_no int not null,
    target_code varchar(40) not null,
    quantity_unit varchar(16) not null,
    shipped_quantity decimal(18,3) not null,
    primary key (bill_id, source_no)
);
