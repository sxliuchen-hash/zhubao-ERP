package com.zhubao.erp.business.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface PortionReleaseRepository {

    Optional<PortionRelease> find(String bagId);

    void save(PortionRelease release, WorkSession session);
}
