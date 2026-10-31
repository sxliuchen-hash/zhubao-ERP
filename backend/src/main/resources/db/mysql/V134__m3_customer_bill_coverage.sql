-- 客户账单使用旧凭据的额度。原应收不改，同一凭据的已用合计不能超过原额。
create table fin_customer_bill_coverage_guard (
    receivable_code varchar(40) not null primary key
);

create table fin_customer_bill_coverage (
    bill_id varchar(40) not null,
    receivable_code varchar(40) not null,
    amount decimal(18,2) not null,
    primary key (bill_id, receivable_code)
);
