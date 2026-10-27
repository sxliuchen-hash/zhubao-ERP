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

    /**
     * 追加一条没有去向的收货冲正，只扣减原收货余额，并记录原流水号。
     * 必须留在当前工作单元里，失败时由工作单元整单回滚。
     */
    default StockLedgerEntry appendInboundReversal(StockLedgerEntry entry, WorkSession session) {
        throw new UnsupportedOperationException("当前仓储未实现收货冲正");
    }

    /** 追加一条没有去向的离厂或冲减，只扣来源余额。 */
    default StockLedgerEntry appendDeparture(StockLedgerEntry entry, WorkSession session) {
        throw new UnsupportedOperationException("当前仓储未实现库存离厂");
    }

    List<StockLedgerEntry> findAllLedgers();

    /**
     * 按客户编号或母单号读取台账。两个条件都为空时返回空列表。
     * 结果仍须按原授权规则过滤，这里只缩小单次读取范围。
     */
    List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes);

    /** 在当前工作单元里读取一张母单的流水，能看见尚未提交的冲正。 */
    default List<StockLedgerEntry> ledgersForMother(String motherOrderCode, WorkSession session) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("库存读取必须使用工作单元连接");
        }
        return findLedgersByCustomers(Set.of(), motherOrderCode == null ? Set.of() : Set.of(motherOrderCode));
    }

    /** 按母单读取流水。lock 为真时必须看到锁定后的当前行，不能沿用旧快照。 */
    default List<StockLedgerEntry> ledgersForMother(String motherOrderCode, WorkSession session, boolean lock) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("库存流水读取必须使用工作单元连接");
        }
        if (session == null) {
            return findLedgersByCustomers(Set.of(), motherOrderCode == null ? Set.of() : Set.of(motherOrderCode));
        }
        return ledgersForMother(motherOrderCode, session);
    }

    /** 按母单读取余额。确认结单时锁定该母单的余额行。 */
    default List<StockBalance> balancesForMother(String motherOrderCode, WorkSession session, boolean lock) {
        if (session != null && session.connection().isPresent()) {
            throw new IllegalStateException("库存余额读取必须使用工作单元连接");
        }
        String mother = motherOrderCode == null ? "" : motherOrderCode;
        return findAllBalances().stream()
                .filter(balance -> balance != null && balance.dimension() != null
                        && mother.equals(balance.dimension().motherOrderCode()))
                .toList();
    }

    /**
     * 母单号与单据号相同时，该母单下任意流水都算已入账。
     * 子单还要看单据号、目标工单号或来源工单号，避免把发货单号误当成没有流水。
     */
    default boolean hasOrderMovement(String motherOrderCode, String orderCode) {
        if (motherOrderCode == null || motherOrderCode.isBlank() || orderCode == null || orderCode.isBlank()) {
            return false;
        }
        return findLedgersByCustomers(Set.of(), Set.of(motherOrderCode)).stream()
                .anyMatch(entry -> OrderMovementQuery.matches(entry, motherOrderCode, orderCode));
    }

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
