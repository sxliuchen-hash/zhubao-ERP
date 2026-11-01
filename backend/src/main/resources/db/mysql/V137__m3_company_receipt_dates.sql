-- 历史入库未保存日期，保持为空，不猜测补写。
alter table inv_company_material_receipt add column business_date varchar(10) null;
alter table inv_company_material_receipt add column recorded_at varchar(40) null;
