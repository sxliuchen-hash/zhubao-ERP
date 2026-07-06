package com.zhubao.erp.inventory.domain;

import java.util.List;

public interface StockLedgerRepository {

    StockLedgerEntry save(StockLedgerEntry entry);

    List<StockLedgerEntry> findAllLedgers();

    List<StockBalance> findAllBalances();

    StockBalance findBalance(StockDimension dimension);

    void clear();
}
