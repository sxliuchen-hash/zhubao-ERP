-- 公开交付访问标识。停用只关闭持码读取，不删除交付事实。
create table biz_public_delivery_access (
    access_id char(64) not null primary key,
    delivery_id varchar(40) not null,
    customer_id bigint not null,
    bag_codes varchar(1000) not null,
    photo_ids varchar(1000) not null,
    enabled boolean not null,
    version_no int not null
);
