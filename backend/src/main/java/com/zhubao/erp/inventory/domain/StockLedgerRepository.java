package com.zhubao.erp.inventory.domain;

import com.zhubao.erp.work.domain.WorkSession;

import java.util.List;
import java.util.Set;

public interface StockLedgerRepository {

    /** 原子校验来源足额并保存台账及双方余额；失败时不得留下部分变更。 */
    StockLedgerEntry save(StockLedgerEntry entry);

    /**
     * 把台账写入当前工作单元，不在这里提交。
     * 内存实现登记撤销动作；JDBC 必须使用会话上的同一条连接。
     */
    StockLedgerEntry save(StockLedgerEntry entry, WorkSession session);

    List<StockLedgerEntry> findAllLedgers();

    /**
     * 按客户编号或母单号读取台账。两个条件都为空时返回空列表。
     * 结果仍须按原授权规则过滤，这里只缩小单次读取范围。
     */
    List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes);

    List<StockBalance> findAllBalances();

    StockBalance findBalance(StockDimension dimension);

    /**
     * 在当前工作单元里读取余额。
     * JDBC 必须走会话上的同一条连接，才能看见本事务尚未提交的转入。
     */
    default StockBalance findBalance(StockDimension dimension, WorkSession session) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("库存读取必须使用工作单元连接");
        }
        return findBalance(dimension);
    }

    void clear();
}
