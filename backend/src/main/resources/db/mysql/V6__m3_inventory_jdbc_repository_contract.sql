-- M3 inventory persistence contract.
-- MySQL unique keys treat nullable columns as distinct, so the original stock
-- dimension unique key is not enough when work_order_code or owner_customer_id
-- is null. The JDBC repository writes a normalized dimension_key for stable
-- lookup and upsert behavior.

alter table inv_stock_balance
    add column dimension_key varchar(500) null after stock_status;

update inv_stock_balance
   set dimension_key = concat_ws('|',
       owner_type,
       coalesce(cast(owner_customer_id as char), ''),
       coalesce(cast(customer_id as char), ''),
       mother_order_code,
       coalesce(work_order_code, ''),
       material_code,
       warehouse_code,
       stock_status
   )
 where dimension_key is null;

alter table inv_stock_balance
    modify column dimension_key varchar(500) not null;

alter table inv_stock_balance
    add unique key uk_stock_balance_dimension_key (dimension_key);
