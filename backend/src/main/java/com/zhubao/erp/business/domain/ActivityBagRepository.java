package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface ActivityBagRepository {

    void save(ActivityBag bag, int expectedVersion, WorkSession session);

    Optional<ActivityBag> find(String id);

    Optional<ActivityBag> lock(String id, WorkSession session);

    List<ActivityBag> listByTarget(String targetCode);

    /**
     * 按来源读取已经分配的袋。默认不支持，正式仓储必须按来源汇总。
     */
    default List<ActivityBag> listBySource(String sourceKey) {
        throw new UnsupportedOperationException("未按来源读取袋");
    }

    /**
     * 锁住同一来源的分配。内存实现不需要数据库锁。
     */
    default void lockSource(String sourceKey, WorkSession session) {
    }
}
