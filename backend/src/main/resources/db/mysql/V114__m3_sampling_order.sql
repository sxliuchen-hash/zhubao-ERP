-- 独立打样单。单号只属于打样，不存放普通加工母单，也不记实物或样费。
create table smp_sampling_order (
    sampling_code varchar(32) not null primary key,
    customer_id bigint not null,
    customer_name varchar(80) not null,
    material_intent varchar(32) not null,
    requirement_text varchar(500) not null,
    owner_name varchar(40) not null,
    status_code varchar(24) not null,
    version_no bigint not null,
    opened_by varchar(64) not null,
    opened_on char(10) not null,
    key idx_smp_sampling_order_customer (customer_id)
);
