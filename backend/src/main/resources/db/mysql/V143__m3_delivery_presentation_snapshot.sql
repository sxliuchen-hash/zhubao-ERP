-- 新交付保存可读的客户、品名规格和加工要求，历史缺项不从当前档案补造。
alter table biz_delivery_draft_line add column customer_name varchar(200) null;
alter table biz_delivery_draft_line add column goods_specification varchar(500) null;
alter table biz_delivery_draft_line add column processing_summary text null;
