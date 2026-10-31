-- 外协确认损耗可以只扣数量。重量未知时流水重量为空，余额重量不减。
alter table inv_stock_ledger modify weight decimal(18,3) null;
