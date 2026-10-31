-- 金工已放行份额的交付。费用状态固定未确认，不在这里记应收。
create table gld_goldwork_delivery (
    delivery_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    quality_id varchar(40) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    fee_state varchar(16) not null,
    constraint uk_gld_delivery_quality unique (quality_id),
    constraint fk_gld_delivery_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code),
    constraint fk_gld_delivery_quality foreign key (quality_id) references gld_goldwork_quality (quality_id)
);
