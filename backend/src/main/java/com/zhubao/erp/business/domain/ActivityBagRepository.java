package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface ActivityBagRepository {

    void save(ActivityBag bag, int expectedVersion, WorkSession session);

    Optional<ActivityBag> find(String id);

    Optional<ActivityBag> lock(String id, WorkSession session);

    List<ActivityBag> listByTarget(String targetCode);
}
