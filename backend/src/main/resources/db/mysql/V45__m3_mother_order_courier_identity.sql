-- 非空运单只能对应一张母单。空运单不写识别键，避免多张无运单母单互相占用。
-- 已有重复运单时本索引会失败，需要先合并母单再升级。

alter table biz_mother_order
    add column courier_identity varchar(80) null;

update biz_mother_order
   set courier_identity = nullif(trim(inbound_express_no), '')
 where courier_identity is null
   and inbound_express_no is not null
   and trim(inbound_express_no) <> '';

create unique index uk_mother_order_courier_identity on biz_mother_order (courier_identity);
