-- 盘点快照。账面数量保留，确认后不重复调整。
create table inv_material_stocktake (
    stocktake_id varchar(40) not null primary key,
    counted_at varchar(40) not null,
    item_id varchar(40) not null,
    batch_code varchar(40) not null,
    location_code varchar(40) not null,
    book_quantity decimal(18,3) not null,
    counted_quantity decimal(18,3) null,
    status varchar(16) not null,
    version_no int not null
);
