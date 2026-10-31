-- 独立金工单。路线快照与抛光（珠）分开，不存放母单号或打样号。
create table gld_goldwork_order (
    goldwork_code varchar(32) not null primary key,
    customer_id bigint not null,
    customer_name varchar(80) not null,
    material_intent varchar(32) not null,
    requirement_text varchar(500) not null,
    owner_name varchar(40) not null,
    route_steps varchar(200) not null,
    status_code varchar(24) not null,
    version_no bigint not null,
    opened_by varchar(64) not null,
    opened_on char(10) not null,
    key idx_gld_goldwork_order_customer (customer_id)
);
