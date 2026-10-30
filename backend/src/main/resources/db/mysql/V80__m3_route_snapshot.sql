-- 三类业务的路线快照。已确认版本按快照保存，不随后来的默认工序表改写。
create table biz_route_snapshot (
    snapshot_id varchar(40) not null primary key,
    business_kind varchar(20) not null,
    target_code varchar(90) not null,
    version_no int not null,
    step_ids varchar(800) not null,
    confirmed boolean not null,
    unique key uk_route_snapshot_target (business_kind, target_code, version_no)
);
