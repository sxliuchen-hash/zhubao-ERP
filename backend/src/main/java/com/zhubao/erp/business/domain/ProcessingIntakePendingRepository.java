package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;
import java.util.List;
import java.util.Optional;

public interface ProcessingIntakePendingRepository {
    Optional<ProcessingIntakePending> find(String code, WorkSession session);
    List<ProcessingIntakePending> list(String account);
    void save(ProcessingIntakePending pending, WorkSession session);
}
