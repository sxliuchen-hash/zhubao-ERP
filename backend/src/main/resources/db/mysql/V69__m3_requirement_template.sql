-- 加工要求组合模板。步骤是文字快照；停用后新单不再提供，已保存的母单要求不改。
create table biz_requirement_template (
    template_id bigint not null auto_increment primary key,
    template_code varchar(32) not null,
    template_name varchar(40) not null,
    normalized_name varchar(40) not null,
    enabled tinyint not null default 1,
    sort_order int not null default 0,
    version int not null default 1,
    request_key varchar(80) null,
    unique key uk_requirement_template_code (template_code),
    unique key uk_requirement_template_name (normalized_name),
    unique key uk_requirement_template_request (request_key)
);

create table biz_requirement_template_step (
    template_id bigint not null,
    step_order int not null,
    phrase varchar(40) not null,
    primary key (template_id, step_order)
);

create table biz_mother_requirement_source (
    mother_order_code varchar(40) not null primary key,
    template_code varchar(32) not null default '',
    template_version int not null default 0,
    saved_requirement varchar(500) not null
);

insert into biz_requirement_template (template_code, template_name, normalized_name, enabled, sort_order, version, request_key)
values
    ('TPL-BEAD-BASIC', '常规珠型加工', '常规珠型加工', 1, 1, 1, null),
    ('TPL-BEAD-CARVE', '珠型雕刻加工', '珠型雕刻加工', 1, 2, 1, null);

insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 1, '开粗' from biz_requirement_template where template_code = 'TPL-BEAD-BASIC';
insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 2, '车形（车成珠型）' from biz_requirement_template where template_code = 'TPL-BEAD-BASIC';
insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 3, '抛光' from biz_requirement_template where template_code = 'TPL-BEAD-BASIC';

insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 1, '开粗' from biz_requirement_template where template_code = 'TPL-BEAD-CARVE';
insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 2, '车形（车成珠型）' from biz_requirement_template where template_code = 'TPL-BEAD-CARVE';
insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 3, '雕刻' from biz_requirement_template where template_code = 'TPL-BEAD-CARVE';
insert into biz_requirement_template_step (template_id, step_order, phrase)
select template_id, 4, '抛光' from biz_requirement_template where template_code = 'TPL-BEAD-CARVE';
