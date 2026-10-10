package com.zhubao.erp.security.domain;

/**
 * 账号动作事务中的失败注入点。生产实现使用 {@link #NONE}。
 */
public interface AuthAccountActionProbe {

    AuthAccountActionProbe NONE = new AuthAccountActionProbe() {
    };

    default void beforeAccountWrite() {
    }

    default void beforeAuditWrite() {
    }

    default void beforeApplicationWrite() {
    }
}
