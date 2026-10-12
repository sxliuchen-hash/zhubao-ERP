package com.zhubao.erp.work.domain;

import java.sql.Connection;
import java.util.Optional;

/** 在调用方连接上读写请求。这些方法不提交、不回滚。 */
public interface CommandRequestStore {

    Optional<CommandRequest> findByRequestId(Connection connection, String requestId);

    void insert(Connection connection, CommandRequest request);

    void updateResult(Connection connection, String requestId, String resultText);
}
