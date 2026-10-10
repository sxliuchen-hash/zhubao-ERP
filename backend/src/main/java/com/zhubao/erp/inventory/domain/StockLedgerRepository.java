package com.zhubao.erp.inventory.domain;

import java.util.List;
import java.util.Set;

public interface StockLedgerRepository {

    /** 原子校验来源足额并保存台账及双方余额；失败时不得留下部分变更。 */
    StockLedgerEntry save(StockLedgerEntry entry);

    List<StockLedgerEntry> findAllLedgers();

    /**
     * 按客户编号或母单号读取台账。两个条件都为空时返回空列表。
     * 结果仍须按原授权规则过滤，这里只缩小单次读取范围。
     */
    List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes);

    List<StockBalance> findAllBalances();

    StockBalance findBalance(StockDimension dimension);

    void clear();
}
