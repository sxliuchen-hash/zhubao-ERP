package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.inventory.domain.CustomerLedgerFilter;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockTransferRules;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "zhubao.inventory.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryStockLedgerRepository implements StockLedgerRepository {

    private final List<StockLedgerEntry> ledgers = new ArrayList<>();
    private final Map<StockDimension, StockBalance> balances = new LinkedHashMap<>();

    @Override
    public synchronized StockLedgerEntry save(StockLedgerEntry entry) {
        return apply(entry);
    }

    @Override
    public synchronized StockLedgerEntry appendInboundReversal(StockLedgerEntry entry, WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("收货冲正必须处于业务工作单元");
        }
        if (entry.to() != null || entry.from() == null || entry.sourceLedgerCode() == null
                || entry.sourceLedgerCode().isBlank() || !"inbound_reversal".equals(entry.documentType())) {
            throw new IllegalArgumentException("收货冲正只能扣减原收货余额，不能转入其他库存状态");
        }
        Map<StockDimension, StockBalance> balancesBefore = new LinkedHashMap<>(balances);
        List<StockLedgerEntry> ledgersBefore = new ArrayList<>(ledgers);
        StockBalance source = findBalance(entry.from());
        StockTransferRules.requireSufficient(source, entry);
        balances.put(entry.from(), nextBalance(source, entry.quantity().negate(), entry.weight().negate()));
        ledgers.add(entry);
        session.onRollback(() -> {
            synchronized (this) {
                balances.clear();
                balances.putAll(balancesBefore);
                ledgers.clear();
                ledgers.addAll(ledgersBefore);
            }
        });
        return entry;
    }

    @Override
    public synchronized StockLedgerEntry appendDeparture(StockLedgerEntry entry, WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("库存离厂必须处于业务工作单元");
        }
        StockTransferRules.verifyDeparture(entry);
        Map<StockDimension, StockBalance> balancesBefore = new LinkedHashMap<>(balances);
        List<StockLedgerEntry> ledgersBefore = new ArrayList<>(ledgers);
        StockBalance source = findBalance(entry.from());
        StockTransferRules.requireSufficient(source, entry);
        balances.put(entry.from(), nextBalance(source, entry.quantity().negate(), entry.weight().negate()));
        ledgers.add(entry);
        session.onRollback(() -> {
            synchronized (this) {
                balances.clear();
                balances.putAll(balancesBefore);
                ledgers.clear();
                ledgers.addAll(ledgersBefore);
            }
        });
        return entry;
    }

    @Override
    public synchronized StockLedgerEntry save(StockLedgerEntry entry, WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("库存写入必须处于业务工作单元");
        }
        Map<StockDimension, StockBalance> balancesBefore = new LinkedHashMap<>(balances);
        List<StockLedgerEntry> ledgersBefore = new ArrayList<>(ledgers);
        StockLedgerEntry saved = apply(entry);
        session.onRollback(() -> {
            synchronized (this) {
                balances.clear();
                balances.putAll(balancesBefore);
                ledgers.clear();
                ledgers.addAll(ledgersBefore);
            }
        });
        return saved;
    }

    private StockLedgerEntry apply(StockLedgerEntry entry) {
        StockTransferRules.verify(entry);
        if (ledgers.stream().anyMatch(existing -> existing.code().equals(entry.code()))) {
            throw new IllegalStateException("库存台账号已经存在");
        }
        // 先完成校验和双方余额计算，避免失败时只写入部分库存状态。
        Map<StockDimension, StockBalance> updates = new LinkedHashMap<>();
        if (entry.from() != null) {
            StockBalance source = findBalance(entry.from());
            StockTransferRules.requireSufficient(source, entry);
            updates.put(entry.from(), nextBalance(source, entry.quantity().negate(), entry.weight().negate()));
        }
        StockBalance destination = updates.getOrDefault(entry.to(), findBalance(entry.to()));
        updates.put(entry.to(), nextBalance(destination, entry.quantity(), entry.weight()));
        balances.putAll(updates);
        ledgers.add(entry);
        return entry;
    }

    @Override
    public synchronized List<StockLedgerEntry> findAllLedgers() {
        return List.copyOf(ledgers);
    }

    @Override
    public synchronized List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
        Set<Long> ids = customerIds == null ? Set.of() : customerIds;
        Set<String> mothers = motherOrderCodes == null ? Set.of() : motherOrderCodes;
        if (ids.isEmpty() && mothers.isEmpty()) {
            return List.of();
        }
        return ledgers.stream().filter(entry -> CustomerLedgerFilter.touches(entry, ids, mothers)).toList();
    }

    @Override
    public synchronized List<StockBalance> findAllBalances() {
        return List.copyOf(balances.values());
    }

    @Override
    public synchronized StockBalance findBalance(StockDimension dimension) {
        return balances.getOrDefault(dimension, new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Override
    public synchronized StockBalance findBalance(StockDimension dimension, WorkSession session) {
        if (session == null) {
            throw new IllegalStateException("库存读取必须处于业务工作单元");
        }
        return findBalance(dimension);
    }

    @Override
    public synchronized void clear() {
        ledgers.clear();
        balances.clear();
    }

    private StockBalance nextBalance(StockBalance current, BigDecimal quantityDelta, BigDecimal weightDelta) {
        return new StockBalance(
                current.dimension(),
                current.quantity().add(quantityDelta),
                current.weight().add(weightDelta)
        );
    }
}
