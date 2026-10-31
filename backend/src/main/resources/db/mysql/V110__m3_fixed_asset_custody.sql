-- 固定资产交接历史。追加前后位置、保管人和状态，不改原值，也不记折旧。
create table fin_fixed_asset_custody (
    transfer_id varchar(80) not null primary key,
    asset_code varchar(80) not null,
    from_location varchar(80) null,
    to_location varchar(80) null,
    from_custodian varchar(80) null,
    to_custodian varchar(80) null,
    from_status varchar(16) null,
    to_status varchar(16) null,
    version_no bigint not null,
    key idx_fin_asset_custody_asset (asset_code, version_no)
);
