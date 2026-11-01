-- 数量去向与实际作业分开保存，历史缺项保持待核，不自动扣损耗或猜测工费数量。
alter table biz_quantity_disposition add column work_quantity decimal(18,3) null;
alter table biz_quantity_disposition add column worker_account varchar(80) null;
alter table biz_quantity_disposition add column worker_name varchar(80) null;
alter table biz_quantity_disposition add column attachment_code varchar(80) null;
