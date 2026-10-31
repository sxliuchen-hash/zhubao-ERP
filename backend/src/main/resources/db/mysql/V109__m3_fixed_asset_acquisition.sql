-- 固定资产购置来源。原值仍只在资产卡片上，付款另记。
-- 空的资产号或应付号表示尚未形成，不能用来冒充已付。
create table fin_fixed_asset_acquisition (
    acquisition_id varchar(80) not null primary key,
    asset_code varchar(80) null,
    source_kind varchar(24) not null,
    amount_state varchar(16) not null,
    supplier_name varchar(80) not null,
    acquired_on char(10) not null,
    handover_location varchar(80) null,
    handover_receiver varchar(80) null,
    payable_code varchar(80) null,
    covered_payable_code varchar(80) null,
    payment_code varchar(80) null,
    payment_voucher varchar(80) null,
    unique key uk_fin_asset_acq_asset (asset_code),
    unique key uk_fin_asset_acq_payable (payable_code),
    unique key uk_fin_asset_acq_covered (covered_payable_code)
);
