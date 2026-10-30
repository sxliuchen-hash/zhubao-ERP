package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

public interface ConsumableAdoptionRepository {

    void save(ConsumableAdoption adoption, WorkSession session);

    Optional<ConsumableAdoption> findByOldCode(String oldItemCode);
}
