package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface ReworkTaskRepository {

    void save(ReworkTask task, WorkSession session);

    Optional<ReworkTask> findByRequest(String requestId);

    List<ReworkTask> listBySource(String sourceBagId);

    List<ReworkTask> listByReworkBag(String reworkBagId);
}
