package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface RouteSnapshotRepository {

    void insert(RouteSnapshot snapshot, WorkSession session);

    Optional<RouteSnapshot> find(String snapshotId);

    List<RouteSnapshot> list(BusinessKind kind, String targetCode);
}
