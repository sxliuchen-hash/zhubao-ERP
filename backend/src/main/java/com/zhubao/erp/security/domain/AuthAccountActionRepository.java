package com.zhubao.erp.security.domain;

/**
 * 账号动作的持久化事务。申请消费、账号变更和账号管理审计一起成功或一起回滚。
 * 内存实现用同一把锁串行化；JDBC 用单连接事务和申请行上的条件更新，多实例不依赖进程内锁。
 */
public interface AuthAccountActionRepository {

    void commit(AuthAccountMutation mutation);
}
