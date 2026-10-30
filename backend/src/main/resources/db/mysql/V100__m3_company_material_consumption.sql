-- 领用去向。耗用数量从尚未结清的领用里扣，金额未知留空。
alter table inv_company_material_issue add column consumed_quantity decimal(18,3) not null default 0;

create table inv_company_material_consumption (
    consumption_id varchar(40) not null primary key,
    issue_id varchar(40) not null,
    consumed_quantity decimal(18,3) not null,
    returned_quantity decimal(18,3) not null,
    kept_quantity decimal(18,3) not null,
    scrap_quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    amount_state varchar(16) not null,
    amount_value decimal(18,2) null,
    target_code varchar(40) not null
);
