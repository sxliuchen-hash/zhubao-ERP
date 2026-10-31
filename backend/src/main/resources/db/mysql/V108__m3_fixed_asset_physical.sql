-- 固定资产实物字段。沿用原资产编码，不复制原值，也不重建财务卡片。
-- 未登记的型号、序列、位置、保管和状态保持空；读取时标待补，不补造历史。
create table fin_fixed_asset_physical (
    asset_code varchar(80) not null primary key,
    model_name varchar(80) null,
    serial_number varchar(80) null,
    location_name varchar(80) null,
    custodian_name varchar(80) null,
    use_status varchar(16) null,
    version_no bigint not null
);
