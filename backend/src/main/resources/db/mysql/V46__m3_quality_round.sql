-- 质检轮次与返工报工事实。初检历史仍留在质检对照表，这里不回写原不合格结果。
-- 旧数据不补复检行。没有轮次记录时，读取端把原对照当作历史首轮。

create table if not exists biz_quality_round (
    id bigint primary key auto_increment,
    round_code varchar(140) not null,
    quality_case_code varchar(120) not null,
    order_code varchar(120) not null,
    mother_order_code varchar(120) not null,
    round_no int not null,
    round_type varchar(20) not null,
    rework_order_code varchar(120) not null default '',
    rework_report_ledger varchar(80) not null default '',
    outcome varchar(20) not null,
    frozen tinyint not null default 0,
    ledger_code varchar(80) not null default '',
    inspector varchar(80) not null,
    created_at varchar(40) not null,
    unique key uk_quality_round_code (round_code),
    unique key uk_quality_round_no (quality_case_code, round_no)
);

create table if not exists biz_quality_round_line (
    id bigint primary key auto_increment,
    round_code varchar(140) not null,
    line_no int not null,
    clause_text varchar(500) not null,
    verdict varchar(20) not null,
    reason varchar(500) not null default '',
    unique key uk_quality_round_line (round_code, line_no)
);

create table if not exists biz_quality_rework_report (
    id bigint primary key auto_increment,
    rework_order_code varchar(120) not null,
    quality_case_code varchar(120) not null,
    order_code varchar(120) not null,
    mother_order_code varchar(120) not null,
    ledger_code varchar(80) not null,
    operator_name varchar(80) not null,
    created_at varchar(40) not null,
    unique key uk_quality_rework_report (rework_order_code)
);
