-- 袋份额的放行和冻结数量。重复放行不另记一份。
create table biz_portion_release (
    bag_id varchar(40) not null primary key,
    released_qty decimal(18,3) not null,
    frozen_qty decimal(18,3) not null
);
