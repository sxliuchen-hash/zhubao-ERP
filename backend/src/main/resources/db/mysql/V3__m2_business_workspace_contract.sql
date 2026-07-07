-- M2 schema draft for business workspace review data.
-- These tables are not the final transactional ERP tables; they define a seed/acceptance
-- persistence target for the current /api/business/workspaces contract.

create table biz_workspace_snapshot (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null unique,
    snapshot_name varchar(120) not null,
    biz_date date not null,
    workshop_name varchar(120) not null,
    shift_name varchar(80) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
);

create table biz_workspace_stat (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    workspace_code varchar(40) not null,
    stat_key varchar(80) not null,
    stat_label varchar(120) not null,
    stat_value varchar(120) not null,
    detail varchar(500) null,
    tone varchar(30) not null,
    unique key uk_workspace_stat (snapshot_code, workspace_code, stat_key),
    index idx_workspace_stat_snapshot (snapshot_code, workspace_code)
);

create table biz_workspace_action_item (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    workspace_code varchar(40) not null,
    item_code varchar(80) not null,
    title varchar(160) not null,
    detail varchar(500) not null,
    owner_name varchar(80) not null,
    next_action varchar(160) not null,
    tone varchar(30) not null,
    unique key uk_workspace_action_item (snapshot_code, item_code),
    index idx_workspace_action (snapshot_code, workspace_code, owner_name)
);

create table biz_workspace_inbound_order (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    mother_order_code varchar(80) not null,
    customer_name varchar(120) not null,
    material_name varchar(160) not null,
    inbound_date_display varchar(40) null,
    express_company varchar(80) null,
    courier_no varchar(80) not null,
    sender_name varchar(120) null,
    receiver_name varchar(120) null,
    unpack_signer varchar(120) null,
    goods_name varchar(160) null,
    quantity int not null,
    weight_display varchar(80) not null,
    order_status varchar(40) not null,
    video_status varchar(40) not null,
    owner_type varchar(40) not null,
    inbound_remark varchar(500) null,
    unique key uk_workspace_inbound (snapshot_code, mother_order_code),
    index idx_workspace_inbound_status (snapshot_code, order_status)
);

create table biz_workspace_work_order (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    work_order_code varchar(80) not null,
    customer_name varchar(120) not null,
    material_name varchar(160) not null,
    process_name varchar(120) not null,
    process_category varchar(60) null,
    product_category varchar(120) null,
    machine_no varchar(80) null,
    work_item_name varchar(120) null,
    started_at_display varchar(40) null,
    finished_at_display varchar(40) null,
    completion_status varchar(120) null,
    handoff_audit_status varchar(80) null,
    assignee_name varchar(80) not null,
    planned_quantity int not null,
    finished_quantity int not null,
    work_status varchar(40) not null,
    progress int not null,
    unique key uk_workspace_work_order (snapshot_code, work_order_code),
    index idx_workspace_work_status (snapshot_code, work_status)
);

create table biz_workspace_handoff (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    handoff_code varchar(80) not null,
    from_node varchar(160) not null,
    to_node varchar(160) not null,
    input_weight_display varchar(80) not null,
    output_weight_display varchar(80) not null,
    loss_rate_display varchar(80) not null,
    handoff_status varchar(40) not null,
    tone varchar(30) not null,
    unique key uk_workspace_handoff (snapshot_code, handoff_code),
    index idx_workspace_handoff_status (snapshot_code, handoff_status)
);

create table biz_workspace_quality_case (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    quality_code varchar(80) not null,
    work_order_code varchar(80) not null,
    process_name varchar(120) not null,
    inspector_name varchar(80) not null,
    defect varchar(500) not null,
    quality_action varchar(80) not null,
    quality_status varchar(80) not null,
    unique key uk_workspace_quality (snapshot_code, quality_code),
    index idx_workspace_quality_status (snapshot_code, quality_status)
);

create table biz_workspace_loss_approval (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    approval_code varchar(80) not null,
    work_order_code varchar(80) not null,
    process_name varchar(120) not null,
    standard_loss varchar(80) not null,
    actual_loss varchar(80) not null,
    evidence varchar(500) not null,
    approval_action varchar(120) not null,
    tone varchar(30) not null,
    unique key uk_workspace_loss (snapshot_code, approval_code),
    index idx_workspace_loss_action (snapshot_code, approval_action)
);

create table biz_workspace_inventory_balance (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    owner_name varchar(120) not null,
    batch_code varchar(80) not null,
    material_name varchar(160) not null,
    location_name varchar(160) not null,
    stock_status varchar(40) not null,
    quantity int not null,
    weight_display varchar(80) not null,
    frozen_display varchar(80) not null,
    unique key uk_workspace_balance (snapshot_code, owner_name, batch_code, stock_status),
    index idx_workspace_balance_status (snapshot_code, stock_status)
);

create table biz_workspace_inventory_ledger (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    ledger_code varchar(80) not null,
    occurred_time varchar(40) not null,
    movement varchar(80) not null,
    before_balance varchar(160) not null,
    after_balance varchar(160) not null,
    source_document varchar(80) not null,
    operator_name varchar(80) not null,
    unique key uk_workspace_ledger (snapshot_code, ledger_code),
    index idx_workspace_ledger_source (snapshot_code, source_document)
);

create table biz_workspace_mobile_task (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    task_code varchar(80) not null,
    scene varchar(120) not null,
    next_action varchar(200) not null,
    required_fields varchar(500) not null,
    task_status varchar(80) not null,
    unique key uk_workspace_mobile_task (snapshot_code, task_code),
    index idx_workspace_mobile_status (snapshot_code, task_status)
);
