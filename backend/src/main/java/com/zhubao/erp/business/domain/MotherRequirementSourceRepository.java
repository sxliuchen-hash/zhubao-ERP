package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface MotherRequirementSourceRepository {

    void save(MotherRequirementSource source, WorkSession session);

    Optional<MotherRequirementSource> find(String motherOrderCode);
}
