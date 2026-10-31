-- 金料来源引用。申报计量可空，精度状态固定为未核定，不在这里记库存余额。
create table gld_goldwork_material (
    line_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    owner_type varchar(16) not null,
    source_id varchar(80) not null,
    source_line_no int null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    gross_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    precision_state varchar(16) not null,
    constraint fk_gld_material_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code)
);
