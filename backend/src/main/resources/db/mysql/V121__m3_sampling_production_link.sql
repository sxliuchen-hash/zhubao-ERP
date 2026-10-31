-- 普通母单对已确认样品版本的引用快照。不改母单加工要求原文。
create table smp_sampling_production_link (
    mother_code varchar(32) not null primary key,
    sampling_code varchar(32) not null,
    version_kind varchar(16) not null,
    version_no int not null,
    snapshot_text varchar(500) not null,
    linked_on char(10) not null,
    constraint fk_smp_link_order foreign key (sampling_code) references smp_sampling_order (sampling_code)
);
