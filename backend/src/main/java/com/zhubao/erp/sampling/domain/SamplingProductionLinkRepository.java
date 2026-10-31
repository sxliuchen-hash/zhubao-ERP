package com.zhubao.erp.sampling.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface SamplingProductionLinkRepository {

    void insert(SamplingProductionLink link, WorkSession session);

    void delete(String motherCode, WorkSession session);

    Optional<SamplingProductionLink> find(String motherCode);

    Optional<SamplingProductionLink> lock(String motherCode, WorkSession session);
}
