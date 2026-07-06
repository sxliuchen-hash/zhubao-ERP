-- M0 schema baseline for MySQL 8.4. Kept as an implementation reference until persistence is wired.

create table inv_stock_ledger (
    id bigint primary key auto_increment,
    code varchar(60) not null unique,
    biz_time datetime(3) not null,
    document_type varchar(50) not null,
    document_code varchar(80) not null,
    owner_type varchar(20) not null,
    owner_customer_id bigint null,
    customer_id bigint null,
    mother_order_code varchar(80) not null,
    work_order_code varchar(90) null,
    material_code varchar(80) not null,
    quantity decimal(18,3) not null,
    weight decimal(18,3) not null,
    from_warehouse_code varchar(50) null,
    to_warehouse_code varchar(50) not null,
    from_stock_status varchar(30) null,
    to_stock_status varchar(30) not null,
    remark text null,
    created_at datetime(3) not null default current_timestamp(3),
    index idx_stock_ledger_mother_time (mother_order_code, biz_time),
    index idx_stock_ledger_document (document_type, document_code)
);

create table inv_stock_balance (
    id bigint primary key auto_increment,
    owner_type varchar(20) not null,
    owner_customer_id bigint null,
    customer_id bigint null,
    mother_order_code varchar(80) not null,
    work_order_code varchar(90) null,
    material_code varchar(80) not null,
    warehouse_code varchar(50) not null,
    stock_status varchar(30) not null,
    available_quantity decimal(18,3) not null default 0,
    available_weight decimal(18,3) not null default 0,
    last_ledger_code varchar(60) null,
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    unique key uk_stock_balance_dimension (
        owner_type,
        owner_customer_id,
        customer_id,
        mother_order_code,
        work_order_code,
        material_code,
        warehouse_code,
        stock_status
    )
);

create table biz_video_clip_task (
    id bigint primary key auto_increment,
    code varchar(60) not null unique,
    biz_type varchar(50) not null,
    biz_code varchar(90) not null,
    camera_code varchar(60) not null,
    start_at datetime(3) not null,
    end_at datetime(3) not null,
    buffer_seconds int not null,
    retry_count int not null default 0,
    status varchar(30) not null,
    storage_key varchar(255) null,
    created_at datetime(3) not null default current_timestamp(3),
    index idx_video_task_biz (biz_type, biz_code),
    index idx_video_task_status (status)
);
