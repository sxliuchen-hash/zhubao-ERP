-- 按原发外行收回。少回仍在外，已确认损耗单独记录，未知重量留空。
create table biz_outsourcing_return (
    return_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    dispatch_id varchar(40) not null,
    party_id varchar(40) not null,
    dispatch_version int not null,
    received_qty decimal(18,3) not null,
    unprocessed_qty decimal(18,3) not null,
    loss_qty decimal(18,3) not null,
    weight decimal(18,3) null,
    weight_known boolean not null,
    received_bag_id varchar(40) not null,
    unprocessed_bag_id varchar(40) not null,
    loss_bag_id varchar(40) not null,
    remainder_bag_id varchar(40) not null,
    unique key uk_outsourcing_return_request (request_id),
    key idx_outsourcing_return_dispatch (dispatch_id)
);
