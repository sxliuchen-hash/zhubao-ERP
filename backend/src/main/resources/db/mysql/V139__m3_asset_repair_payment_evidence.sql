-- 每次维修付款保存独立凭据，历史未保存的凭据不补造。
create table fin_fixed_asset_repair_payment (
    payment_code varchar(80) not null primary key,
    repair_id varchar(80) not null,
    voucher varchar(240) not null,
    key idx_asset_repair_payment (repair_id)
);
