-- 客户按次账单。初始账单每个发货单最多一张，调整另记同根差额，不改原应收。
create table fin_customer_delivery_bill (
    bill_id varchar(40) not null primary key,
    request_id varchar(64) not null,
    root_bill_id varchar(40) not null,
    delivery_id varchar(40) not null,
    customer_id bigint not null,
    bill_kind varchar(16) not null,
    total_amount decimal(18,2) not null,
    version_no int not null,
    initial_delivery_id varchar(40) null,
    unique key uk_customer_delivery_bill_request (request_id),
    unique key uk_customer_delivery_bill_initial (initial_delivery_id)
);

create table fin_customer_delivery_bill_line (
    bill_id varchar(40) not null,
    line_no int not null,
    item_name varchar(80) not null,
    method_code varchar(32) not null,
    amount decimal(18,2) not null,
    expression_text varchar(200) not null,
    primary key (bill_id, line_no)
);
