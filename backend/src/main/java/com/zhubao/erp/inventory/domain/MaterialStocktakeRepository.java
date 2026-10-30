package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.math.BigDecimal;
import java.util.Optional;

public interface MaterialStocktakeRepository {

    void insert(MaterialStocktake stocktake, WorkSession session);

    Optional<MaterialStocktake> find(String stocktakeId);

    void count(String stocktakeId, BigDecimal countedQuantity, int expectedVersion, WorkSession session);

    void confirm(String stocktakeId, int expectedVersion, WorkSession session);
}
