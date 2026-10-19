-- 收货录入错误冲正只追加库存流水，并用 source_ledger_code 指回原流水。
-- 没有去向的行只扣减原来的余额，不转入报废、冻结或其他库存状态。

alter table inv_stock_ledger
    add column source_ledger_code varchar(60) null;

alter table inv_stock_ledger
    modify column to_warehouse_code varchar(50) null;

alter table inv_stock_ledger
    modify column to_stock_status varchar(30) null;

create index idx_stock_ledger_source on inv_stock_ledger (source_ledger_code);
