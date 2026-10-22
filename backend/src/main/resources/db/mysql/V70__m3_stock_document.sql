-- 公司料和客户寄存的正式单据。批次仍走原库存维度，单位记在明细上，不改写旧的八段库存键。
create table inv_stock_document (
    document_code varchar(40) not null primary key,
    document_kind varchar(40) not null,
    status varchar(20) not null,
    owner_type varchar(20) not null,
    customer_id bigint null,
    customer_name varchar(80) not null default '',
    warehouse_code varchar(40) not null,
    source_note varchar(200) not null default '',
    handover_person varchar(40) not null default '',
    purpose varchar(200) not null default '',
    recipient varchar(40) not null default '',
    target_order_code varchar(40) not null default '',
    source_document_code varchar(40) not null default '',
    operator_name varchar(40) not null,
    request_id varchar(80) not null,
    biz_date varchar(20) not null,
    created_at varchar(40) not null,
    evidence_code varchar(80) not null default '',
    direction varchar(20) not null default '',
    unique key uk_stock_document_request (request_id),
    key idx_stock_document_source (source_document_code)
);

create table inv_stock_document_line (
    document_code varchar(40) not null,
    line_no int not null,
    material_code varchar(40) not null,
    quantity_unit varchar(20) not null,
    quantity decimal(18, 3) not null,
    weight decimal(18, 3) null,
    batch_code varchar(64) not null default '',
    external_ref varchar(80) not null default '',
    source_line_no int null,
    counted tinyint not null default 0,
    declared_quantity decimal(18, 3) not null,
    declared_weight decimal(18, 3) null,
    ledger_code varchar(40) not null default '',
    primary key (document_code, line_no),
    key idx_stock_line_batch (batch_code)
);
