package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.DeliveryDraftRepository;
import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillRepository;
import com.zhubao.erp.finance.domain.FinanceContinuityView;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceiptAllocationOperation;
import com.zhubao.erp.finance.domain.ReceivableLine;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * 读取已确认加工费、收款分摊和员工应付余额。查询不写应收、工资或退款。
 */
@Service
public class FinanceContinuityQuery {

    private final FinanceRepository finance;
    private final CustomerDeliveryBillRepository bills;
    private final DeliveryDraftRepository drafts;

    public FinanceContinuityQuery(FinanceRepository finance, CustomerDeliveryBillRepository bills) {
        this(finance, bills, null);
    }

    @Autowired
    public FinanceContinuityQuery(FinanceRepository finance, CustomerDeliveryBillRepository bills, DeliveryDraftRepository drafts) {
        this.finance = finance;
        this.bills = bills;
        this.drafts = drafts;
    }

    public FinanceContinuityView read(List<String> confirmedDeliveryIds, List<String> closedTargetCodes) {
        Set<String> deliveryIds = new HashSet<>(confirmedDeliveryIds == null ? List.of() : confirmedDeliveryIds);
        Set<String> targets = new HashSet<>(closedTargetCodes == null ? List.of() : closedTargetCodes);
        Set<String> billIds = billIds(deliveryIds);
        Set<String> incomeCodes = new HashSet<>();
        BigDecimal income = BigDecimal.ZERO;
        Set<String> requiredTargets = new TreeSet<>(targets);
        Map<String, BigDecimal> legacyCoverage = new HashMap<>();
        for (String deliveryId : deliveryIds) {
            var initial = bills.findInitial(deliveryId).orElse(null);
            if (initial != null) {
                for (var coverage : bills.coverages(initial.billId())) {
                    legacyCoverage.merge(coverage.receivableCode(), coverage.amount(), BigDecimal::add);
                }
                var history = bills.listByRoot(initial.rootBillId());
                var latest = history.get(history.size() - 1);
                income = income.add(latest.totalAmount());
                for (var line : bills.lines(latest.billId())) {
                    if (!line.targetCode().isBlank()) requiredTargets.add(line.targetCode());
                    for (var source : line.coverageSources()) requiredTargets.add(source.targetCode());
                }
            }
            if (drafts != null) {
                for (var line : drafts.lines(deliveryId)) requiredTargets.add(line.targetCode());
            }
        }
        for (ReceivableLine receivable : finance.listReceivables()) {
            if (!"DELIVERY_BILL".equals(receivable.sourceType()) || receivable.sourceKey() == null
                    || !receivable.sourceKey().startsWith("delivery-bill:")) {
                continue;
            }
            if (billIds.contains(receivable.sourceKey().substring("delivery-bill:".length()))) {
                incomeCodes.add(receivable.code());
            }
        }
        Map<String, BigDecimal> allocated = new HashMap<>();
        for (ReceiptAllocationOperation operation : finance.listAllocationOperations()) {
            BigDecimal sign = ReceiptAllocationOperation.REVOKE.equals(operation.action())
                    ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            for (ReceiptAllocation line : operation.lines()) {
                allocated.merge(line.receivableCode(), line.amount().multiply(sign), BigDecimal::add);
            }
        }
        BigDecimal collected = incomeCodes.stream().map(code -> allocated.getOrDefault(code, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> receiptPending = new ArrayList<>();
        for (var coverage : legacyCoverage.entrySet()) {
            var source = finance.findReceivableByCode(coverage.getKey()).orElse(null);
            BigDecimal received = allocated.getOrDefault(coverage.getKey(), BigDecimal.ZERO);
            if (source == null) {
                receiptPending.add(coverage.getKey());
            } else if (coverage.getValue().compareTo(source.amount()) == 0) {
                collected = collected.add(received);
            } else if (received.compareTo(source.amount()) == 0) {
                collected = collected.add(coverage.getValue());
            } else if (received.signum() != 0) {
                // 部分收款只核销到旧应收，不能擅自决定归属哪一次分批交付。
                receiptPending.add(coverage.getKey());
            }
        }
        BigDecimal labor = BigDecimal.ZERO;
        for (PayrollLine line : finance.listPayrollLines()) {
            if (line.confirmed() && inTarget(line.sourceKey(), targets)) {
                labor = labor.add(line.netAmount());
            }
        }
        Set<String> wagePayables = new HashSet<>();
        for (Payable payable : finance.listPayables()) {
            if ("工资".equals(payable.kind()) && inScope(payable, targets)) {
                wagePayables.add(payable.code());
            }
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (PayablePayment payment : finance.listPayablePayments()) {
            if (wagePayables.contains(payment.payableCode())) {
                paid = paid.add(payment.amount());
            }
        }
        List<String> awaitingBills = new ArrayList<>();
        for (String deliveryId : confirmedDeliveryIds == null ? List.<String>of() : confirmedDeliveryIds) {
            if (bills.findInitial(deliveryId).isEmpty()) {
                awaitingBills.add(deliveryId);
            }
        }
        List<String> awaitingSettlement = new ArrayList<>();
        for (String target : requiredTargets) {
            String prefix = "employee-target:" + target + ":";
            boolean settled = finance.listPayrollLines().stream()
                    .anyMatch(line -> line.confirmed() && line.sourceKey() != null && line.sourceKey().startsWith(prefix));
            if (!targets.contains(target) || !settled) {
                awaitingSettlement.add(target);
            }
        }
        boolean costIncomplete = !awaitingSettlement.isEmpty() || (!deliveryIds.isEmpty() && requiredTargets.isEmpty());
        BigDecimal uncollected = receiptPending.isEmpty() ? income.subtract(collected) : null;
        return new FinanceContinuityView(
                income, receiptPending.isEmpty() ? collected : null, uncollected, labor, paid, labor.subtract(paid),
                List.copyOf(awaitingBills), List.copyOf(awaitingSettlement), costIncomplete,
                costIncomplete ? null : income.subtract(labor), receiptPending.stream().sorted().toList());
    }

    /** 应付余额来自已确认工资应付减去付款，不把付款再计一次成本。 */
    public BigDecimal unpaidLabor(String payableCode) {
        Payable payable = finance.findPayable(payableCode).orElse(null);
        if (payable == null || !"工资".equals(payable.kind())) {
            return BigDecimal.ZERO;
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (PayablePayment payment : finance.listPayablePayments()) {
            if (payableCode.equals(payment.payableCode())) {
                paid = paid.add(payment.amount());
            }
        }
        return payable.amount().subtract(paid);
    }

    private Set<String> billIds(Set<String> deliveryIds) {
        Set<String> billIds = new HashSet<>();
        for (String deliveryId : deliveryIds) {
            bills.findInitial(deliveryId).ifPresent(initial -> {
                for (CustomerDeliveryBill bill : bills.listByRoot(initial.rootBillId())) {
                    if (deliveryId.equals(bill.deliveryId())) {
                        billIds.add(bill.billId());
                    }
                }
            });
        }
        return billIds;
    }

    private static boolean inTarget(String sourceKey, Set<String> targets) {
        if (sourceKey == null) {
            return false;
        }
        for (String target : targets) {
            if (target != null && !target.isBlank() && sourceKey.startsWith("employee-target:" + target + ":")) {
                return true;
            }
        }
        return false;
    }

    private static boolean inScope(Payable payable, Set<String> targets) {
        return targets.contains(payable.sourceCode()) || targets.contains(payable.motherOrderCode());
    }
}
