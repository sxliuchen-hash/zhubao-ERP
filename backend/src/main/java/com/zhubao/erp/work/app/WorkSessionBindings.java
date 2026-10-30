package com.zhubao.erp.work.app;

import com.zhubao.erp.work.domain.WorkSession;

import java.sql.Connection;

/**
 * 新仓储接入工作单元时使用的连接约定。
 * 先由工作单元占用命令请求，再在同一次会话里写业务。
 * JDBC 写入只能使用这次会话给出的连接；另开的连接不属于本命令，失败时也不会随本命令回滚。
 */
public final class WorkSessionBindings {

    private WorkSessionBindings() {
    }

    public static Connection requiredJdbc(WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("JDBC 写入必须处在工作单元内");
        }
        return session.connection().orElseThrow(() -> new IllegalStateException("JDBC 写入必须使用工作单元给出的连接"));
    }
}
