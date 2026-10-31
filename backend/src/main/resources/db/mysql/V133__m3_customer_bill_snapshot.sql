-- 客户账单行保存计费原输入、有效数量和当时的舍入规则，供同一快照复算。
alter table fin_customer_delivery_bill_line add column target_code varchar(40) null;
alter table fin_customer_delivery_bill_line add column quantity_unit varchar(16) null;
alter table fin_customer_delivery_bill_line add column direction_code varchar(16) null;
alter table fin_customer_delivery_bill_line add column input_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column unit_price decimal(18,4) null;
alter table fin_customer_delivery_bill_line add column base_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column exempt_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column direct_amount decimal(18,2) null;
alter table fin_customer_delivery_bill_line add column bill_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column basis_text varchar(200) null;
alter table fin_customer_delivery_bill_line add column shipped_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column loss_quantity decimal(18,3) null;
alter table fin_customer_delivery_bill_line add column coverage_unit varchar(16) null;
alter table fin_customer_delivery_bill_line add column quantity_scale int null;
alter table fin_customer_delivery_bill_line add column price_scale int null;
alter table fin_customer_delivery_bill_line add column money_scale int null;
alter table fin_customer_delivery_bill_line add column rounding_mode varchar(16) null;
