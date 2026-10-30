-- 工具库位和借出。借出不改变公司所有权。
create table inv_tool_stock (
    item_id varchar(40) not null,
    warehouse_code varchar(40) not null,
    quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    primary key (item_id, warehouse_code)
);

create table inv_tool_piece (
    piece_code varchar(40) not null primary key,
    item_id varchar(40) not null,
    warehouse_code varchar(40) not null,
    holder_id varchar(40) not null
);

create table inv_tool_loan (
    loan_id varchar(40) not null primary key,
    item_id varchar(40) not null,
    holder_id varchar(40) not null,
    holder_name varchar(80) not null,
    quantity decimal(18,3) not null,
    returned_quantity decimal(18,3) not null,
    quantity_unit varchar(16) not null,
    piece_code varchar(40) not null,
    warehouse_code varchar(40) not null,
    version_no int not null
);
