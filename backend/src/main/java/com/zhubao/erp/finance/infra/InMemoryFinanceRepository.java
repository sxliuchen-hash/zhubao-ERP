package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ConsumableItem;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinancePaymentProbe;
import com.zhubao.erp.finance.domain.FinanceReceiptProbe;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.WorkerRate;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ConcurrentHashMap<String, String> paymentCodeByIdempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> receiptCodeByIdempotency = new ConcurrentHashMap<>();
    private final Object paymentLock = new Object();
    private final Object receiptLock = new Object();
    private final FinancePaymentProbe paymentProbe;
    private final FinanceReceiptProbe receiptProbe;

    @Autowired
    public InMemoryFinanceRepository() {
        this(FinancePaymentProbe.NONE, FinanceReceiptProbe.NONE);
    }

    public InMemoryFinanceRepository(FinancePaymentProbe paymentProbe) {
        this(paymentProbe, FinanceReceiptProbe.NONE);
    }

    public InMemoryFinanceRepository(FinancePaymentProbe paymentProbe, FinanceReceiptProbe receiptProbe) {
        this.paymentProbe = paymentProbe == null ? FinancePaymentProbe.NONE : paymentProbe;
        this.receiptProbe = receiptProbe == null ? FinanceReceiptProbe.NONE : receiptProbe;
    }

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
    public void saveReceivable(ReceivableLine line, WorkSession session) {
        ReceivableLine previousLine = receivablesByCode.get(line.code());
        String previousSource = receivableCodeBySource.get(line.sourceKey());
        saveReceivable(line);
        if (session != null) {
            session.onRollback(() -> {
                if (previousLine == null) {
                    receivablesByCode.remove(line.code());
                } else {
                    receivablesByCode.put(line.code(), previousLine);
                }
                if (previousSource == null) {
                    receivableCodeBySource.remove(line.sourceKey());
                } else {
                    receivableCodeBySource.put(line.sourceKey(), previousSource);
                }
            });
        }
    }

    @Override
    public boolean hasMotherPosting(String motherOrderCode) {
        return receivablesByCode.values().stream().anyMatch(line -> motherOrderCode.equals(line.motherOrderCode()))
                || payrollLines.values().stream().anyMatch(line -> motherOrderCode.equals(line.motherOrderCode()))
                || payables.values().stream().anyMatch(line -> motherOrderCode.equals(line.motherOrderCode()));
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
    public CustomerReceipt registerReceipt(CustomerReceipt receipt, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        synchronized (receiptLock) {
            if (!key.isEmpty()) {
                String existingCode = receiptCodeByIdempotency.get(key);
                if (existingCode != null) {
                    CustomerReceipt existing = receipts.get(existingCode);
                    if (existing == null || !sameReceipt(existing, receipt)) {
                        throw new BusinessException("同一幂等键的收款内容不一致");
                    }
                    return existing;
                }
            }
            assertOpenBalances(receipt);
            receipts.put(receipt.code(), receipt);
            if (!key.isEmpty()) {
                receiptCodeByIdempotency.put(key, receipt.code());
            }
            try {
                receiptProbe.beforeAllocationInsert();
            } catch (RuntimeException ex) {
                receipts.remove(receipt.code());
                if (!key.isEmpty()) {
                    receiptCodeByIdempotency.remove(key);
                }
                throw ex;
            }
            return receipt;
        }
    }

    @Override
    public Optional<CustomerReceipt> findReceiptByIdempotency(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String code = receiptCodeByIdempotency.get(key);
        return code == null ? Optional.empty() : Optional.ofNullable(receipts.get(code));
    }

    private void assertOpenBalances(CustomerReceipt receipt) {
        Map<String, BigDecimal> requested = new LinkedHashMap<>();
        for (ReceiptAllocation allocation : receipt.allocations()) {
            if (allocation.amount() == null || allocation.amount().signum() < 0) {
                throw new BusinessException("核销金额不能为负");
            }
            requested.merge(allocation.receivableCode(), allocation.amount(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
            ReceivableLine line = receivablesByCode.get(entry.getKey());
            if (line == null) {
                throw new BusinessException("找不到要核销的账单行");
            }
            if (!line.reversesCode().isBlank()) {
                throw new BusinessException("红字不能直接核销");
            }
            BigDecimal open = line.amount();
            for (ReceivableLine other : receivablesByCode.values()) {
                if (line.code().equals(other.reversesCode())) {
                    open = open.add(other.amount());
                }
            }
            for (CustomerReceipt existing : receipts.values()) {
                for (ReceiptAllocation allocation : existing.allocations()) {
                    if (line.code().equals(allocation.receivableCode())) {
                        open = open.subtract(allocation.amount());
                    }
                }
            }
            if (entry.getValue().compareTo(open) > 0) {
                throw new BusinessException("核销金额不能超过尚未核销的余额");
            }
        }
    }

    private boolean sameReceipt(CustomerReceipt existing, CustomerReceipt requested) {
        if (existing.customerId() != requested.customerId()
                || existing.amount().compareTo(requested.amount()) != 0
                || !existing.receivedOn().equals(requested.receivedOn())
                || !existing.customerName().equals(requested.customerName())
                || existing.allocations().size() != requested.allocations().size()) {
            return false;
        }
        List<ReceiptAllocation> left = existing.allocations().stream()
                .sorted(Comparator.comparing(ReceiptAllocation::receivableCode).thenComparing(ReceiptAllocation::amount))
                .toList();
        List<ReceiptAllocation> right = requested.allocations().stream()
                .sorted(Comparator.comparing(ReceiptAllocation::receivableCode).thenComparing(ReceiptAllocation::amount))
                .toList();
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).receivableCode().equals(right.get(index).receivableCode())
                    || left.get(index).amount().compareTo(right.get(index).amount()) != 0) {
                return false;
            }
        }
        return true;
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
        payrollLines.entrySet().removeIf(entry -> yearMonth.equals(entry.getValue().yearMonth())
                && (entry.getValue().sourceKey() == null || !entry.getValue().sourceKey().startsWith("PAY-ADJ|")));
        for (PayrollLine line : lines) {
            payrollLines.put(line.code(), line);
        }
    }

    @Override
    public void savePayrollLine(PayrollLine line) {
        payrollLines.put(line.code(), line);
    }

    @Override
    public void savePayrollLine(PayrollLine line, WorkSession session) {
        PayrollLine previous = payrollLines.get(line.code());
        savePayrollLine(line);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    payrollLines.remove(line.code());
                } else {
                    payrollLines.put(line.code(), previous);
                }
            });
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
    public void savePayable(Payable payable, WorkSession session) {
        Payable previous = payables.get(payable.code());
        savePayable(payable);
        if (session != null) {
            session.onRollback(() -> {
                if (previous == null) {
                    payables.remove(payable.code());
                } else {
                    payables.put(payable.code(), previous);
                }
            });
        }
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
    public PayablePayment registerPayablePayment(PayablePayment payment, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        synchronized (paymentLock) {
            if (!key.isEmpty()) {
                String existingCode = paymentCodeByIdempotency.get(key);
                if (existingCode != null) {
                    PayablePayment existing = payablePayments.get(existingCode);
                    if (existing == null || !samePayment(existing, payment)) {
                        throw new BusinessException("同一幂等键的付款内容不一致");
                    }
                    return existing;
                }
            }
            Payable payable = payables.get(payment.payableCode());
            if (payable == null) {
                throw new BusinessException("找不到应付");
            }
            BigDecimal nextPaid = payable.paidAmount().add(payment.amount());
            if (nextPaid.compareTo(payable.amount()) > 0) {
                throw new BusinessException("付款金额不能超过尚未支付的余额");
            }
            paymentProbe.beforePaymentInsert();
            payablePayments.put(payment.code(), payment);
            if (!key.isEmpty()) {
                paymentCodeByIdempotency.put(key, payment.code());
            }
            try {
                paymentProbe.beforeBalanceUpdate();
                payables.put(payable.code(), payable.pay(nextPaid));
            } catch (RuntimeException ex) {
                payablePayments.remove(payment.code());
                if (!key.isEmpty()) {
                    paymentCodeByIdempotency.remove(key);
                }
                throw ex;
            }
            return payment;
        }
    }

    private boolean samePayment(PayablePayment existing, PayablePayment requested) {
        return existing.payableCode().equals(requested.payableCode())
                && existing.amount().compareTo(requested.amount()) == 0
                && existing.paidOn().equals(requested.paidOn());
    }

    @Override
    public List<PayablePayment> listPayablePayments() {
        return new ArrayList<>(payablePayments.values());
    }
}
