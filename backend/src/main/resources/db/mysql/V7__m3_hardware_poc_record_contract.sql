-- M3 hardware POC record contract.
-- This table captures site validation evidence before any strong hardware integration is promised.

create table hw_poc_record (
    id bigint primary key auto_increment,
    poc_code varchar(80) not null unique,
    device_code varchar(80) not null,
    scene varchar(160) not null,
    test_window varchar(120) not null,
    result varchar(40) not null,
    evidence varchar(500) not null,
    blocker varchar(500) not null,
    owner_name varchar(80) not null,
    next_action varchar(500) not null,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3),
    index idx_hw_poc_device (device_code),
    index idx_hw_poc_result (result),
    index idx_hw_poc_owner (owner_name)
);
