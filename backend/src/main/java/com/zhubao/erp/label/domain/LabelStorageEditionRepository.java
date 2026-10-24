package com.zhubao.erp.label.domain;

import com.zhubao.erp.work.domain.WorkSession;
import java.util.Optional;

public interface LabelStorageEditionRepository {
    Optional<LabelStorageEdition> latest(String code, WorkSession session);
    java.util.List<LabelStorageEdition> history(String code);
    void save(LabelStorageEdition edition, WorkSession session);
}
