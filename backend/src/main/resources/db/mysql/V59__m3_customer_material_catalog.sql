-- 稳定客户身份和可复用材质目录。旧单据及授权保持原值，待人工核对归属。
create table biz_customer_profile (
    customer_id bigint not null auto_increment primary key,
    customer_code varchar(40) not null,
    display_name varchar(120) not null,
    normalized_name varchar(120) not null,
    aliases varchar(500) not null default '',
    contact_name varchar(120) not null default '',
    phone varchar(80) not null default '',
    remark varchar(500) not null default '',
    enabled boolean not null default true,
    unique key uk_customer_profile_code (customer_code),
    unique key uk_customer_profile_name (normalized_name)
);

insert into biz_customer_profile (customer_id, customer_code, display_name, normalized_name, enabled) values
    (1001, 'C-1001', '恒信珠宝', '恒信珠宝', true),
    (1002, 'C-1002', '雅辰饰品', '雅辰饰品', true),
    (1003, 'C-1003', '璟瑞工贸', '璟瑞工贸', true),
    (1004, 'C-1004', '瑞禾珠宝', '瑞禾珠宝', true);

create table biz_material_catalog (
    material_code varchar(80) not null primary key,
    display_name varchar(120) not null,
    normalized_name varchar(120) not null,
    aliases varchar(500) not null default '',
    remark varchar(500) not null default '',
    enabled boolean not null default true,
    unique key uk_material_catalog_name (normalized_name)
);

insert into biz_material_catalog (material_code, display_name, normalized_name, enabled)
values ('AU9999', '足金', '足金', true);
