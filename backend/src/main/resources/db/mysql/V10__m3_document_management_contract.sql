create table if not exists doc_package (
    id bigint primary key auto_increment,
    code varchar(80) not null unique,
    title varchar(120) not null,
    category varchar(50) not null,
    business_code varchar(80) not null,
    owner_name varchar(80) not null,
    purpose varchar(500) not null,
    required_evidence int not null,
    linked_evidence int not null,
    status varchar(50) not null,
    retention_policy varchar(200) not null,
    last_updated varchar(30) not null,
    tone varchar(20) not null,
    created_at timestamp default current_timestamp
);

create table if not exists doc_evidence_attachment (
    id bigint primary key auto_increment,
    code varchar(80) not null unique,
    document_code varchar(80) not null,
    business_type varchar(50) not null,
    business_code varchar(80) not null,
    file_name varchar(200) not null,
    media_type varchar(80) not null,
    storage_location varchar(500) not null,
    storage_policy varchar(120) not null,
    source varchar(80) not null,
    status varchar(50) not null,
    owner_name varchar(80) not null,
    checksum varchar(120) not null,
    uploaded_at varchar(30) not null,
    fallback_action varchar(300) not null,
    tone varchar(20) not null,
    created_at timestamp default current_timestamp,
    index idx_doc_evidence_document_code (document_code),
    index idx_doc_evidence_business (business_type, business_code)
);

create table if not exists doc_audit_event (
    id bigint primary key auto_increment,
    event_code varchar(80) not null unique,
    document_code varchar(80) not null,
    event_time varchar(30) not null,
    action varchar(80) not null,
    operator_name varchar(80) not null,
    detail varchar(500) not null,
    tone varchar(20) not null,
    created_at timestamp default current_timestamp,
    index idx_doc_audit_document_code (document_code)
);
