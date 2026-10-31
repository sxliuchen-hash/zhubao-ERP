-- 固定资产逐件盘点。快照保留。确认不改原值，漏扫或未见也不自动报废。
create table fin_fixed_asset_stocktake (
    stocktake_id varchar(80) not null primary key,
    asset_code varchar(80) not null,
    counted_at varchar(40) not null,
    book_location varchar(80) null,
    book_custodian varchar(80) null,
    book_status varchar(16) null,
    book_version bigint not null,
    seen_state varchar(16) null,
    count_location varchar(80) null,
    count_custodian varchar(80) null,
    exception_text varchar(200) not null,
    status varchar(16) not null,
    version_no bigint not null
);
