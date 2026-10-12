package com.zhubao.erp.work.domain;

import java.sql.Connection;
import java.util.Optional;

/**
 * 一次业务工作单元里的会话。JDBC 提供同一条未提交连接；内存用撤销动作代替。
 */
public interface WorkSession {

    Optional<Connection> connection();

    void onRollback(Runnable undo);
}
