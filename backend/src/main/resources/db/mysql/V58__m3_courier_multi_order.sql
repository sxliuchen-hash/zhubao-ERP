-- 运单号只是可重复的来源线索，保留普通索引供候选查询。
drop index uk_mother_order_courier_identity on biz_mother_order;
create index idx_mother_order_courier_identity on biz_mother_order (courier_identity);
create table biz_courier_multi_order_revision (
    revision_code varchar(16) primary key
);
insert into biz_courier_multi_order_revision (revision_code) values ('V58');
