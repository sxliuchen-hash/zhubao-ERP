-- 管理员维护母单、子单和打印任务。回收只隐藏，不删除库存、账单和历史调整。
alter table biz_mother_order
    add column recycled_at varchar(40) null,
    add column recycled_by varchar(80) null,
    add column recycle_batch varchar(40) null,
    add column recycle_reason varchar(200) null;

alter table biz_work_order
    add column recycled_at varchar(40) null,
    add column recycled_by varchar(80) null,
    add column recycle_batch varchar(40) null,
    add column recycle_reason varchar(200) null;

alter table biz_print_job
    add column recycled_at varchar(40) null,
    add column recycled_by varchar(80) null,
    add column recycle_batch varchar(40) null,
    add column recycle_reason varchar(200) null;

alter table biz_print_target_job
    add column recycled_at varchar(40) null,
    add column recycled_by varchar(80) null,
    add column recycle_batch varchar(40) null,
    add column recycle_reason varchar(200) null;

create table if not exists biz_order_adjustment (
    id bigint primary key auto_increment,
    target_type varchar(20) not null,
    target_code varchar(90) not null,
    before_text varchar(1000) not null,
    after_text varchar(1000) not null,
    reason varchar(200) not null,
    operator_name varchar(80) not null,
    created_at varchar(40) not null,
    key idx_order_adjustment_target (target_type, target_code)
);
