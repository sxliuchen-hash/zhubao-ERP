package com.zhubao.erp.security.domain;

/**
 * 客户身份动作的持久化事务。账号、绑定和审计一起成功或一起回滚。
 */
public interface CustomerPortalIdentityActionRepository {

    void commit(CustomerPortalIdentityAction action);
}
