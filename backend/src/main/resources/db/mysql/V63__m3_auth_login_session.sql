-- 登录会话随账号库保存。只存令牌摘要，退出、过期或吊销后删除；服务重启后原登录仍可恢复。
create table if not exists auth_login_session (
    id bigint primary key auto_increment,
    token_hash varchar(64) not null,
    username varchar(64) not null,
    operator varchar(128) not null,
    role_code varchar(64) not null,
    expires_at varchar(40) not null,
    account_fingerprint varchar(64) not null,
    token_suffix varchar(16) not null,
    created_at varchar(40) not null,
    unique key uk_auth_login_session_token (token_hash),
    key idx_auth_login_session_username (username)
);
