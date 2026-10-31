package com.zhubao.erp.finance.infra;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.CustomerBillCoverage;
import com.zhubao.erp.finance.domain.CustomerChargeMethod;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillLine;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@ConditionalOnProperty(name = "zhubao.customer-delivery-bill.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryCustomerDeliveryBillRepository implements CustomerDeliveryBillRepository {

    private final CopyOnWriteArrayList<CustomerDeliveryBill> bills = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, List<CustomerDeliveryBillLine>> lines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<CustomerBillCoverage>> coverages = new ConcurrentHashMap<>();
    private final Object coverageGate = new Object();

    @Override
    public void save(CustomerDeliveryBill bill, List<CustomerDeliveryBillLine> billLines, WorkSession session) {
        if (bills.stream().anyMatch(row -> row.billId().equals(bill.billId()) || row.requestId().equals(bill.requestId()))) {
            throw new BusinessException("客户账单已存在");
        }
        if (bill.kind() == CustomerDeliveryBillKind.INITIAL && findInitial(bill.deliveryId()).isPresent()) {
            throw new BusinessException("同一已确认发货单只能有一张初始客户账单");
        }
        List<CustomerDeliveryBillLine> stored = List.copyOf(billLines);
        bills.add(bill);
        lines.put(bill.billId(), stored);
        if (session != null) {
            session.onRollback(() -> {
                bills.remove(bill);
                lines.remove(bill.billId(), stored);
            });
        }
    }

    @Override
    public Optional<CustomerDeliveryBill> find(String billId) {
        return bills.stream().filter(row -> row.billId().equals(billId)).findFirst();
    }

    @Override
    public Optional<CustomerDeliveryBill> findByRequest(String requestId) {
        return bills.stream().filter(row -> row.requestId().equals(requestId)).findFirst();
    }

    @Override
    public Optional<CustomerDeliveryBill> findInitial(String deliveryId) {
        return bills.stream()
                .filter(row -> row.deliveryId().equals(deliveryId) && row.kind() == CustomerDeliveryBillKind.INITIAL)
                .findFirst();
    }

    @Override
    public List<CustomerDeliveryBill> listByRoot(String rootBillId) {
        return bills.stream().filter(row -> row.rootBillId().equals(rootBillId))
                .sorted(Comparator.comparingInt(CustomerDeliveryBill::version))
                .toList();
    }

    @Override
    public List<CustomerDeliveryBillLine> lines(String billId) {
        return lines.getOrDefault(billId, List.of());
    }

    @Override
    public void saveCoverage(CustomerBillCoverage coverage, BigDecimal receivableAmount, WorkSession session) {
        synchronized (coverageGate) {
            BigDecimal used = coveredTotal(coverage.receivableCode());
            if (used.add(coverage.amount()).compareTo(receivableAmount) > 0) {
                throw new BusinessException("覆盖金额超过旧凭据剩余额度");
            }
            List<CustomerBillCoverage> stored = new ArrayList<>(coverages.getOrDefault(coverage.receivableCode(), List.of()));
            stored.add(coverage);
            coverages.put(coverage.receivableCode(), List.copyOf(stored));
            if (session != null) {
                session.onRollback(() -> {
                    synchronized (coverageGate) {
                        List<CustomerBillCoverage> current = new ArrayList<>(
                                coverages.getOrDefault(coverage.receivableCode(), List.of()));
                        current.remove(coverage);
                        coverages.put(coverage.receivableCode(), List.copyOf(current));
                    }
                });
            }
        }
    }

    @Override
    public BigDecimal coveredTotal(String receivableCode) {
        return coverages.getOrDefault(receivableCode, List.of()).stream()
                .map(CustomerBillCoverage::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public List<CustomerBillCoverage> coverages(String billId) {
        return coverages.values().stream().flatMap(List::stream)
                .filter(coverage -> billId.equals(coverage.billId())).toList();
    }
}
