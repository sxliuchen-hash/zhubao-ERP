-- 附件来源类型与业务证据引用。object_type 为空表示旧附件来源不确定，只能回看。
-- 引用的工序、条款用空字符串而不是 NULL。
-- 物理唯一索引按 utf8mb4 估算：32+90+16+16+32+96+20+32+80 = 414 个字符，414*4 = 1656 字节，低于 InnoDB 3072 字节上限。
-- link_key_hash 另做定长唯一索引，应用层命中摘要后仍核对原始维度。

alter table doc_evidence_attachment
    add column object_type varchar(32) null;

create table if not exists doc_business_evidence_link (
    id bigint primary key auto_increment,
    link_code varchar(40) not null,
    attachment_code varchar(80) not null,
    target_type varchar(32) not null,
    target_code varchar(90) not null,
    stage varchar(16) not null,
    scene varchar(16) not null,
    operation_type varchar(32) not null,
    operation_code varchar(96) not null,
    process_name varchar(20) not null,
    clause_no varchar(32) not null,
    operator_name varchar(80) not null,
    linked_at varchar(30) not null,
    link_key_hash char(64) not null,
    created_at timestamp default current_timestamp,
    unique key uk_evidence_link_code (link_code),
    unique key uk_evidence_link_hash (link_key_hash),
    unique key uk_evidence_link_dim (
        target_type, target_code, stage, scene, operation_type, operation_code,
        process_name, clause_no, attachment_code
    ),
    index idx_evidence_link_target (target_type, target_code),
    index idx_evidence_link_operation (operation_type, operation_code)
);
