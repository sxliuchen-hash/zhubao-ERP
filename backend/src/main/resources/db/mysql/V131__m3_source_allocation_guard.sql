-- 同一收货或领用来源的分配锁。只串行化初始分袋，不另记库存，也不重复收货。
create table biz_source_allocation_guard (
    source_key varchar(80) not null primary key
);
