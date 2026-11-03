-- 称重记录只追加。原收货重量和已有记录不因后来的称重被覆盖。
create table biz_bag_weight_record (
    bag_id varchar(40) not null,
    version_no int not null,
    weight_state varchar(20) not null,
    weight_value decimal(18,3) null,
    primary key (bag_id, version_no)
);
