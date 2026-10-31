package com.zhubao.erp.finance.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.Optional;

/** 资产逐件盘点。确认不覆盖原快照。 */
public interface FixedAssetStocktakeRepository {

    void insert(FixedAssetStocktake stocktake, WorkSession session);

    Optional<FixedAssetStocktake> find(String stocktakeId);

    void count(String stocktakeId, String seen, String location, String custodian, String exceptionText,
               long expectedVersion, WorkSession session);

    void confirm(String stocktakeId, long expectedVersion, WorkSession session);
}
