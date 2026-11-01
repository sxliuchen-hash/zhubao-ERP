package com.zhubao.erp.goldwork.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Optional;

public interface GoldworkOperationRepository {

    void insert(GoldworkOperationReport report, WorkSession session);

    Optional<GoldworkOperationReport> find(String reportId);

    default Optional<GoldworkOperationReport> lock(String reportId, WorkSession session) {
        return find(reportId);
    }

    boolean photoUsed(String attachmentCode, WorkSession session);

    List<GoldworkOperationReport> list(String goldworkCode);
}
