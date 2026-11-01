-- 同一放行份额允许分次交付，应用在报工来源锁内核对累计数量。
create index idx_gld_delivery_quality on gld_goldwork_delivery (quality_id);
alter table gld_goldwork_delivery drop index uk_gld_delivery_quality;
