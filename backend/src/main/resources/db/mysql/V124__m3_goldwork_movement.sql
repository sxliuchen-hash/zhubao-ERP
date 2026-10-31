-- 金工投入、产出、在制和明确处置。不在这里推算损耗，也不记库存余额。
create table gld_goldwork_movement (
    movement_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    step_id varchar(40) not null,
    movement_kind varchar(16) not null,
    source_line_id varchar(40) not null,
    owner_type varchar(16) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    gross_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    disposition varchar(24) null,
    conversion_note varchar(200) null,
    precision_state varchar(16) not null,
    constraint fk_gld_movement_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code),
    constraint fk_gld_movement_material foreign key (source_line_id) references gld_goldwork_material (line_id)
);
