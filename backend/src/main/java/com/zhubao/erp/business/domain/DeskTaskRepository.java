package com.zhubao.erp.business.domain;

import java.util.List;
import java.util.Optional;

/**
 * 轻任务存储。创建请求号、完成请求号和作废请求号各自唯一。
 */
public interface DeskTaskRepository {

    DeskTask insert(DeskTask task);

    /**
     * 仅当当前版本等于 expectedVersion 时写入。版本不符返回空，不覆盖。
     */
    Optional<DeskTask> updateIfVersion(DeskTask task, int expectedVersion);

    Optional<DeskTask> findByCode(String taskCode);

    Optional<DeskTask> findByRequestId(String requestId);

    Optional<DeskTask> findByCompleteRequestId(String requestId);

    Optional<DeskTask> findByCancelRequestId(String requestId);

    List<DeskTask> listAll();
}
