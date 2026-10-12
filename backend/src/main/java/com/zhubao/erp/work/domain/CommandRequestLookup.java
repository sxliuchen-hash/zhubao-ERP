package com.zhubao.erp.work.domain;

import java.util.Optional;

/** 已提交的命令请求。事务中的读写走 JDBC 仓储上的连接方法。 */
public interface CommandRequestLookup {

    Optional<CommandRequest> findByRequestId(String requestId);
}
