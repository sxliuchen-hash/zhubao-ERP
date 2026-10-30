-- 耗材领用当时的计耗规则，以及只追加的费用事件。
create table inv_consumable_issue_rule (
    issue_id varchar(40) not null primary key,
    timing varchar(32) not null,
    cost_posted boolean not null,
    receipt_id varchar(40) not null
);

create table inv_consumable_cost_event (
    event_id varchar(40) not null primary key,
    issue_id varchar(40) not null,
    kind varchar(16) not null,
    amount_state varchar(16) not null,
    amount_value decimal(18,2) null,
    quantity decimal(18,3) not null
);
