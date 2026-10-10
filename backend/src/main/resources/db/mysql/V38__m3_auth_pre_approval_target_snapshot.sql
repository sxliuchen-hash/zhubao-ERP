-- 账号前置审批保存目标工序与具体启停、解锁动作，执行时与申请快照核对。
-- 历史申请保留空动作；状态动作需重新申请，不能把旧状态单当成任意启停或解锁许可。
alter table auth_account_pre_approval_application
    add column target_allowed_processes text null,
    add column status_action varchar(16) not null default '';
