-- 未称损耗后原重量仅作账面保留，不再表示剩余实物已称重。
alter table inv_stock_balance add column weight_pending boolean not null default false;

-- 接续已存在的未称损耗，不改原台账或原重量。
update inv_stock_balance b set weight_pending = true
where exists (
    select 1 from inv_stock_ledger l
    where l.document_type = 'outsourcing_loss' and l.weight is null
      and l.owner_type = b.owner_type
      and coalesce(l.owner_customer_id, 0) = coalesce(b.owner_customer_id, 0)
      and coalesce(l.customer_id, 0) = coalesce(b.customer_id, 0)
      and coalesce(l.mother_order_code, '') = coalesce(b.mother_order_code, '')
      and coalesce(l.work_order_code, '') = coalesce(b.work_order_code, '')
      and l.material_code = b.material_code
      and l.from_warehouse_code = b.warehouse_code
      and l.from_stock_status = b.stock_status
      and coalesce(l.from_batch_code, '') = coalesce(b.batch_code, '')
);
