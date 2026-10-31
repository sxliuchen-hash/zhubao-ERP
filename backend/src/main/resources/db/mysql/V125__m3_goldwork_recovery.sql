-- 回收、余料、实物报废和不可恢复损耗。不并进成品产出，也不记费用。
create table gld_goldwork_recovery (
    recovery_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    source_line_id varchar(40) not null,
    output_movement_id varchar(40) not null,
    recovery_kind varchar(24) not null,
    owner_type varchar(16) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    gross_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    precision_state varchar(16) not null,
    constraint fk_gld_recovery_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code),
    constraint fk_gld_recovery_material foreign key (source_line_id) references gld_goldwork_material (line_id),
    constraint fk_gld_recovery_output foreign key (output_movement_id) references gld_goldwork_movement (movement_id)
);
