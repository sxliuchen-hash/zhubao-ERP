-- 袋的适用重量。待复称不是 0，补录重量不重复工序数量。
create table biz_bag_weight (
    bag_id varchar(40) not null primary key,
    weight_state varchar(20) not null,
    weight_value decimal(18,3) null,
    version_no int not null
);
