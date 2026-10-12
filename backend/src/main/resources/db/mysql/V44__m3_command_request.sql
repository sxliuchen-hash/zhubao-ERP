-- 正式命令的请求回放。一个请求编号全局只占一行，结果与业务写入必须在同一次提交。
-- 结果为空只会出现在尚未提交的事务里。提交后必须写上成功结果。
-- 本表不替代库存台账或出货单，现有报工、质检、发货命令尚未接入。

create table if not exists biz_command_request (
    id bigint primary key auto_increment,
    request_id varchar(64) not null,
    actor_id varchar(64) not null,
    action_code varchar(64) not null,
    target_type varchar(32) not null,
    target_code varchar(90) not null,
    payload_hash char(64) not null,
    result_text varchar(2000) not null,
    created_at timestamp default current_timestamp,
    unique key uk_command_request_id (request_id)
);
