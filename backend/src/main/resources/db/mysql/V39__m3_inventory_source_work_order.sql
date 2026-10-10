-- 库存台账独立保存来源工单号，支持待料母单分配到子单及不拆母单首次报工。
-- 历史 JDBC 台账只允许工单号不变，有来源的历史行可由目标工单号完整回填。
alter table inv_stock_ledger
    add column from_work_order_code varchar(90) null;

update inv_stock_ledger
   set from_work_order_code = work_order_code
 where from_warehouse_code is not null
   and from_stock_status is not null;
