-- 金工份额质量。一轮一条，不改报工成色，也不记费用。
create table gld_goldwork_quality (
    quality_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    report_id varchar(40) not null,
    prior_quality_id varchar(40) null,
    round_no int not null,
    outcome varchar(16) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    note_text varchar(200) null,
    constraint fk_gld_quality_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code),
    constraint fk_gld_quality_report foreign key (report_id) references gld_goldwork_operation (report_id)
);
