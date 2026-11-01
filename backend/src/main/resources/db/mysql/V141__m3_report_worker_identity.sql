-- 报工保存实际账号，不从显示姓名推测旧记录的计酬人。
alter table biz_whole_bag_report add column worker_account varchar(80) null;
create index idx_whole_bag_report_target on biz_whole_bag_report (target_code);
