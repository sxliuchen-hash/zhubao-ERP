-- 本厂物品档案。规格和单位是可独立领用的身份，材质名称不参与合并。
create table inv_factory_item (
    item_id varchar(40) not null primary key,
    category varchar(32) not null,
    material_name varchar(80) not null,
    spec_code varchar(40) not null,
    quantity_unit varchar(16) not null,
    consumption_timing varchar(32) not null,
    custody_mode varchar(32) not null,
    enabled boolean not null,
    version_no int not null,
    unique (category, material_name, spec_code, quantity_unit)
);
