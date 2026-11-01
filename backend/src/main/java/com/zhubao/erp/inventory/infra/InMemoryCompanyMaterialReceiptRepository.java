package com.zhubao.erp.inventory.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalance;
import com.zhubao.erp.inventory.domain.CompanyMaterialBalances;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceipt;
import com.zhubao.erp.inventory.domain.CompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "zhubao.company-material-receipt.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCompanyMaterialReceiptRepository implements CompanyMaterialReceiptRepository {

    private final ConcurrentHashMap<String, CompanyMaterialReceipt> receipts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompanyMaterialBalance> balances = new ConcurrentHashMap<>();

    @Override
    public void insert(CompanyMaterialReceipt receipt, WorkSession session) {
        CompanyMaterialReceipt previous = receipts.putIfAbsent(receipt.receiptId(), receipt);
        if (previous != null) {
            throw new BusinessException("公司料入库单已存在");
        }
        if (session != null) {
            session.onRollback(() -> receipts.remove(receipt.receiptId(), receipt));
        }
    }

    @Override
    public void add(CompanyMaterialBalance incoming, WorkSession session) {
        String key = key(incoming.itemId(), incoming.batchCode(), incoming.warehouseCode());
        synchronized (balances) {
            CompanyMaterialBalance current = balances.get(key);
            balances.put(key, CompanyMaterialBalances.merge(current, incoming));
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (balances) {
                        if (current == null) {
                            balances.remove(key);
                        } else {
                            balances.put(key, current);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void take(String itemId, String batchCode, String warehouseCode, BigDecimal quantity, String unit,
                     StockMeasurement.Weight weight, WorkSession session) {
        String key = key(itemId, batchCode, warehouseCode);
        synchronized (balances) {
            CompanyMaterialBalance current = balances.get(key);
            if (current == null || current.quantity().compareTo(quantity) < 0) {
                throw new BusinessException("可领数量不足");
            }
            if (!current.unit().equals(unit)) {
                throw new BusinessException("单位必须与来源批次一致");
            }
            CompanyMaterialBalances.assertFullMovementWeight(current, quantity, weight);
            StockMeasurement.Weight left = CompanyMaterialBalances.remainder(current.weight(), weight);
            BigDecimal leftQuantity = current.quantity().subtract(quantity);
            if (leftQuantity.signum() == 0) {
                balances.remove(key);
            } else {
                balances.put(key, new CompanyMaterialBalance(itemId, batchCode, warehouseCode, leftQuantity, unit, left));
            }
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (balances) {
                        balances.put(key, current);
                    }
                });
            }
        }
    }

    @Override
    public Optional<CompanyMaterialReceipt> find(String receiptId) {
        return Optional.ofNullable(receipts.get(receiptId));
    }

    @Override
    public Optional<CompanyMaterialBalance> balance(String itemId, String batchCode, String warehouseCode) {
        return Optional.ofNullable(balances.get(key(itemId, batchCode, warehouseCode)));
    }

    @Override
    public boolean coversPayable(String payableCode, WorkSession session) {
        if (payableCode == null || payableCode.isBlank()) {
            return false;
        }
        return receipts.values().stream().anyMatch(receipt -> payableCode.equals(receipt.coveredPayableCode())
                || payableCode.equals(receipt.payableCode()));
    }

    private static String key(String itemId, String batchCode, String warehouseCode) {
        return itemId + "\n" + batchCode + "\n" + warehouseCode;
    }
}
