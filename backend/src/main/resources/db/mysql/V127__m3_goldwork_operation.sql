-- 金工节点报工。照片编号不重复使用，这里不记库存或工资。
create table gld_goldwork_operation (
    report_id varchar(40) not null primary key,
    goldwork_code varchar(32) not null,
    step_id varchar(40) not null,
    piece_count decimal(24,6) null,
    net_weight decimal(24,6) null,
    weight_unit varchar(16) null,
    purity_text varchar(40) null,
    attachment_code varchar(80) not null,
    worker_name varchar(64) not null,
    reported_on char(10) not null,
    precision_state varchar(16) not null,
    constraint uk_gld_operation_photo unique (attachment_code),
    constraint fk_gld_operation_order foreign key (goldwork_code) references gld_goldwork_order (goldwork_code)
);
