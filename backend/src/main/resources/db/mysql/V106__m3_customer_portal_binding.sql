-- 客户入口绑定。一个账号只对应一个稳定客户主体，不保存名称或自填编号。
create table auth_customer_portal_binding (
    username varchar(64) not null primary key,
    customer_id bigint not null,
    enabled boolean not null,
    bound_by varchar(80) not null,
    bound_at varchar(40) not null,
    unique key uk_auth_customer_portal_customer (customer_id)
);
