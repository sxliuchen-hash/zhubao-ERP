package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface BagWeightRepository {

    Optional<BagWeightFact> find(String bagId);

    void save(BagWeightFact fact, WorkSession session);
}
