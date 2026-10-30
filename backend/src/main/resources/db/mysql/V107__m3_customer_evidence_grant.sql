-- 客户证据获准。没有获准就不对外展示，不修改附件原件。
create table doc_customer_evidence_grant (
    grant_id varchar(40) not null primary key,
    customer_id bigint not null,
    attachment_code varchar(80) not null,
    kind varchar(20) not null,
    unique key uk_doc_customer_evidence (customer_id, attachment_code)
);
