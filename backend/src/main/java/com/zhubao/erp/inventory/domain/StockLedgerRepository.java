package com.zhubao.erp.inventory.domain;

import java.util.List;

public interface StockLedgerRepository {

    /** 原子校验来源足额并保存台账及双方余额；失败时不得留下部分变更。 */
    StockLedgerEntry save(StockLedgerEntry entry);

    List<StockLedgerEntry> findAllLedgers();

    List<StockBalance> findAllBalances();

    StockBalance findBalance(StockDimension dimension);

    void clear();
}
