-- 打样分次交付。不保存普通发货单，也不在这里记样费。
create table smp_sampling_delivery (
    delivery_id varchar(40) not null primary key,
    sampling_code varchar(32) not null,
    version_kind varchar(16) not null,
    version_no int not null,
    material_use_id varchar(40) null,
    quantity decimal(18,3) not null,
    unit_name varchar(16) not null,
    credential_code varchar(80) not null,
    delivered_on char(10) not null,
    constraint fk_smp_delivery_order foreign key (sampling_code) references smp_sampling_order (sampling_code)
);
