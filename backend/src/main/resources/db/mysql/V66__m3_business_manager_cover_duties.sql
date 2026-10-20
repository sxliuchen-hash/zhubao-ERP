-- 业务管理账号单独保存勾选的顶岗范围。空值表示尚未勾选。

alter table auth_user_account
    add column cover_duties varchar(160) not null default '';
