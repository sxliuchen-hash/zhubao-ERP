package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.WorkerRate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经营核算的内存仓储。本地默认使用，方便评审和测试。
 */
@Repository
@ConditionalOnProperty(name = "zhubao.finance.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryFinanceRepository implements FinanceRepository {

    private final ConcurrentHashMap<String, PriceAgreement> agreements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReceivableLine> receivablesByCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> receivableCodeBySource = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CustomerReceipt> receipts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkerRate> workerRates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PayrollLine> payrollLines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PayrollRun> payrollRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumableItem> consumableItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumableMovement> consumableMovements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FixedAsset> assets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DepreciationEntry> depreciations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Payable> payables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PayablePayment> payablePayments = new ConcurrentHashMap<>();

    @Override
    public void saveAgreement(PriceAgreement agreement) {
        agreements.put(agreement.code(), agreement);
    }

    @Override
    public List<PriceAgreement> listAgreements() {
        return agreements.values().stream()
                .sorted(Comparator.comparing(PriceAgreement::code))
                .toList();
    }

    @Override
    public void saveReceivable(ReceivableLine line) {
        String existingCode = receivableCodeBySource.putIfAbsent(line.sourceKey(), line.code());
        if (existingCode != null && !existingCode.equals(line.code())) {
            throw new BusinessException("这张单据已经入过账");
        }
        receivablesByCode.put(line.code(), line);
    }

    @Override
    public List<ReceivableLine> listReceivables() {
        return receivablesByCode.values().stream()
                .sorted(Comparator.comparing(ReceivableLine::code))
                .toList();
    }

    @Override
    public Optional<ReceivableLine> findReceivableByCode(String code) {
        return Optional.ofNullable(receivablesByCode.get(code));
    }

    @Override
    public Optional<ReceivableLine> findReceivableBySourceKey(String sourceKey) {
        String code = receivableCodeBySource.get(sourceKey);
        return code == null ? Optional.empty() : Optional.ofNullable(receivablesByCode.get(code));
    }

    @Override
    public void saveReceipt(CustomerReceipt receipt) {
        receipts.put(receipt.code(), receipt);
    }

    @Override
    public List<CustomerReceipt> listReceipts() {
        return receipts.values().stream()
                .sorted(Comparator.comparing(CustomerReceipt::code))
                .toList();
    }

    @Override
    public void saveWorkerRate(WorkerRate rate) {
        workerRates.put(rate.code(), rate);
    }

    @Override
    public List<WorkerRate> listWorkerRates() {
        return workerRates.values().stream()
                .sorted(Comparator.comparing(WorkerRate::code))
                .toList();
    }

    @Override
    public void replacePayroll(String yearMonth, List<PayrollLine> lines) {
        payrollLines.entrySet().removeIf(entry -> yearMonth.equals(entry.getValue().yearMonth()));
        for (PayrollLine line : lines) {
            payrollLines.put(line.code(), line);
        }
    }

    @Override
    public List<PayrollLine> listPayrollLines() {
        return payrollLines.values().stream()
                .sorted(Comparator.comparing(PayrollLine::code))
                .toList();
    }

    @Override
    public Optional<PayrollRun> findPayrollRun(String yearMonth) {
        return Optional.ofNullable(payrollRuns.get(yearMonth));
    }

    @Override
    public void savePayrollRun(PayrollRun run) {
        payrollRuns.put(run.yearMonth(), run);
    }

    @Override
    public void saveConsumableItem(ConsumableItem item) {
        consumableItems.put(item.code(), item);
    }

    @Override
    public List<ConsumableItem> listConsumableItems() {
        return consumableItems.values().stream()
                .sorted(Comparator.comparing(ConsumableItem::code))
                .toList();
    }

    @Override
    public Optional<ConsumableItem> findConsumableItem(String code) {
        return Optional.ofNullable(consumableItems.get(code));
    }

    @Override
    public void saveConsumableMovement(ConsumableMovement movement) {
        consumableMovements.put(movement.code(), movement);
    }

    @Override
    public List<ConsumableMovement> listConsumableMovements() {
        return consumableMovements.values().stream()
                .sorted(Comparator.comparing(ConsumableMovement::code))
                .toList();
    }

    @Override
    public void saveAsset(FixedAsset asset) {
        assets.put(asset.code(), asset);
    }

    @Override
    public List<FixedAsset> listAssets() {
        return assets.values().stream()
                .sorted(Comparator.comparing(FixedAsset::code))
                .toList();
    }

    @Override
    public void saveDepreciation(DepreciationEntry entry) {
        String key = entry.assetCode() + "|" + entry.yearMonth();
        if (depreciations.putIfAbsent(key, entry) != null) {
            throw new BusinessException("这个月的折旧已经生成");
        }
    }

    @Override
    public List<DepreciationEntry> listDepreciations() {
        return depreciations.values().stream()
                .sorted(Comparator.comparing(DepreciationEntry::code))
                .toList();
    }

    @Override
    public void savePayable(Payable payable) {
        payables.put(payable.code(), payable);
    }

    @Override
    public List<Payable> listPayables() {
        return payables.values().stream()
                .sorted(Comparator.comparing(Payable::code))
                .toList();
    }

    @Override
    public Optional<Payable> findPayable(String code) {
        return Optional.ofNullable(payables.get(code));
    }

    @Override
    public Optional<Payable> findPayableBySource(String sourceCode) {
        return payables.values().stream()
                .filter(payable -> sourceCode.equals(payable.sourceCode()))
                .findFirst();
    }

    @Override
    public void savePayablePayment(PayablePayment payment) {
        payablePayments.put(payment.code(), payment);
    }

    @Override
    public List<PayablePayment> listPayablePayments() {
        return new ArrayList<>(payablePayments.values());
    }
}
