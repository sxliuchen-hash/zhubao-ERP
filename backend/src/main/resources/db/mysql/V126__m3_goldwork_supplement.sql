-- 金工补料和退补。另记来源，不改最初来料，也不在这里记补料费。
create table gld_goldwork_supplement (
    supplement_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    original_line_id varchar(40) not null,
    prior_supplement_id varchar(40) null,
    direction varchar(16) not null,
    owner_type varchar(16) not null,
    source_id varchar(80) not null,
    source_line_no int null,
    purpose_text varchar(200) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    gross_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    precision_state varchar(16) not null,
    constraint fk_gld_supplement_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code),
    constraint fk_gld_supplement_material foreign key (original_line_id) references gld_goldwork_material (line_id)
);
