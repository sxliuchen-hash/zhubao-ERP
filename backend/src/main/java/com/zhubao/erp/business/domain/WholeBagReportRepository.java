package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface WholeBagReportRepository {

    void save(WholeBagReport report, WorkSession session);

    Optional<WholeBagReport> findByRequest(String requestId);

    java.util.List<WholeBagReport> listByTarget(String targetCode);
}
