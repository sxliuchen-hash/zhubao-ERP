-- 公司料领用和退回。退回关联原领用，不另建客户来料。
create table inv_company_material_issue (
    issue_id varchar(40) not null primary key,
    item_id varchar(40) not null,
    batch_code varchar(40) not null,
    from_warehouse varchar(40) not null,
    workshop_code varchar(40) not null,
    quantity decimal(18,3) not null,
    returned_quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null,
    target_code varchar(40) not null,
    version_no int not null
);

create table inv_company_material_return (
    return_id varchar(40) not null primary key,
    issue_id varchar(40) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null
);
