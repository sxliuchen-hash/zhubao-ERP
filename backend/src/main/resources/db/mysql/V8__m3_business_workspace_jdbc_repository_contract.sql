-- M3 business workspace JDBC repository contract extension.
-- V3 defines most review-workspace tables; this extension adds the trace timeline
-- required by /api/business/workspaces/inventory.

create table biz_workspace_trace_event (
    id bigint primary key auto_increment,
    snapshot_code varchar(80) not null,
    event_time varchar(40) not null,
    node_name varchar(120) not null,
    biz_code varchar(80) not null,
    operator_name varchar(80) not null,
    evidence varchar(200) not null,
    unique key uk_workspace_trace_event (snapshot_code, event_time, biz_code),
    index idx_workspace_trace_biz (snapshot_code, biz_code)
);
