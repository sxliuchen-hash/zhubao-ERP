-- 账号的班组和部门使用稳定编号。空值不代表全公司。

alter table auth_user_account
    add column team_code varchar(40) not null default '',
    add column department_code varchar(40) not null default '';
