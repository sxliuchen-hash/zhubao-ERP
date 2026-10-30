-- 实物袋份额。关闭的父袋保留来源，活动数量只统计未关闭的叶袋。
create table biz_activity_bag (
    bag_id varchar(40) not null primary key,
    target_code varchar(90) not null,
    material_code varchar(80) not null,
    requirement_version varchar(40) not null,
    source_key varchar(80) not null,
    parent_id varchar(40) not null,
    closed boolean not null,
    quantity decimal(18,3) not null,
    weight decimal(18,3) null,
    weight_known boolean not null,
    version_no int not null,
    key idx_activity_bag_target (target_code)
);
