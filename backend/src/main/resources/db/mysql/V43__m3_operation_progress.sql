-- 现场过程说明。每次追加一行，不覆盖历史，也不写工序交付、质检、工资或出货。
-- 工序和条款用空字符串。补充标记只说明这是完成后的补记，不改变正式单据状态。
-- 重试唯一键按 utf8mb4 估算：64+64+32+90 个字符 * 4 + char(64)*4 = 1256 字节，低于 InnoDB 3072 上限。
-- 该键只合并同操作者、同目标、同内容的重试。内容或目标变化不能靠它合并，跨单拒绝在写入前检查。

create table if not exists scan_operation_progress (
    id bigint primary key auto_increment,
    progress_code varchar(40) not null,
    target_type varchar(32) not null,
    target_code varchar(90) not null,
    mother_order_code varchar(90) not null,
    stage varchar(16) not null,
    process_name varchar(20) not null,
    clause_no varchar(32) not null,
    note varchar(500) not null,
    operator_id varchar(64) not null,
    operator_name varchar(80) not null,
    operator_role varchar(40) not null,
    supplementary tinyint not null,
    recorded_at varchar(30) not null,
    request_id varchar(64) not null,
    content_hash char(64) not null,
    created_at timestamp default current_timestamp,
    unique key uk_scan_progress_code (progress_code),
    unique key uk_scan_progress_replay (
        request_id, operator_id, target_type, target_code, content_hash
    ),
    index idx_scan_progress_target (target_type, target_code),
    index idx_scan_progress_request (request_id)
);
