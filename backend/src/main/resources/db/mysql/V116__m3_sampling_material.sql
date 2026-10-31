-- 打样领用只引用真实库存移动。不在这里保存另一套实物余额。实际投入未登记时为空。
create table smp_sampling_material_use (
    use_id varchar(40) not null primary key,
    sampling_code varchar(32) not null,
    owner_type varchar(16) not null,
    source_id varchar(80) not null,
    source_line_no int null,
    stock_move_id varchar(80) not null,
    material_code varchar(80) not null,
    batch_code varchar(40) not null,
    quantity decimal(18,3) not null,
    unit_name varchar(16) not null,
    weight_state varchar(16) not null,
    weight_value decimal(18,3) null,
    returned_quantity decimal(18,3) not null,
    input_quantity decimal(18,3) null,
    version_no bigint not null,
    constraint fk_smp_material_order foreign key (sampling_code) references smp_sampling_order (sampling_code)
);
