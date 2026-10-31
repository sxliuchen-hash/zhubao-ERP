-- 固定资产停用、报废和处置事实。不删除原卡，不生成收款，也不写折旧生效月。
create table fin_fixed_asset_disposition (
    disposition_id varchar(80) not null primary key,
    asset_code varchar(80) not null,
    disposition_kind varchar(16) not null,
    occurred_on char(10) not null,
    basis_text varchar(200) not null,
    receipt_code varchar(80) null,
    unique key uk_fin_asset_disposition_receipt (receipt_code),
    key idx_fin_asset_disposition_asset (asset_code)
);
