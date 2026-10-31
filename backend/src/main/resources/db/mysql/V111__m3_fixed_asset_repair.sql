-- 固定资产维修。费用确认后才有一笔应付。付款不在这里再记成本，维修也不报废资产。
create table fin_fixed_asset_repair (
    repair_id varchar(80) not null primary key,
    asset_code varchar(80) not null,
    repaired_on char(10) not null,
    handover_name varchar(80) not null,
    evidence_code varchar(80) not null,
    repair_state varchar(16) not null,
    payable_code varchar(80) null,
    unique key uk_fin_asset_repair_payable (payable_code),
    key idx_fin_asset_repair_asset (asset_code)
);
