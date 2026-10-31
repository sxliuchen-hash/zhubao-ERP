-- 一次交付的样费或免收。免收不写金额。不在这里记工资。
create table smp_sampling_fee (
    fee_id varchar(40) not null primary key,
    delivery_id varchar(40) not null,
    sampling_code varchar(32) not null,
    decision_code varchar(16) not null,
    amount decimal(18,2) null,
    reason_text varchar(200) null,
    receivable_code varchar(80) null,
    unique key uk_smp_fee_delivery (delivery_id),
    constraint fk_smp_fee_delivery foreign key (delivery_id) references smp_sampling_delivery (delivery_id)
);
