-- 加工要求快捷词。全厂共用；删除只影响以后点选，不改已经写入母单的要求。
create table biz_requirement_shortcut (
    shortcut_id bigint not null auto_increment primary key,
    phrase varchar(40) not null,
    normalized_phrase varchar(40) not null,
    sort_order int not null default 0,
    unique key uk_requirement_shortcut_phrase (normalized_phrase)
);
