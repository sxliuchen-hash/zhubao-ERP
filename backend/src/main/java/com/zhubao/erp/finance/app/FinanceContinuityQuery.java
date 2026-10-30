package com.zhubao.erp.finance.app;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取已确认加工费、收款分摊和员工应付余额。查询不写应收、工资或退款。
 */
@Service
public class FinanceContinuityQuery {

    private final FinanceRepository finance;
    private final CustomerDeliveryBillRepository bills;

    public FinanceContinuityQuery(FinanceRepository finance, CustomerDeliveryBillRepository bills) {
        this.finance = finance;
        this.bills = bills;
    }

    public FinanceContinuityView read(List<String> confirmedDeliveryIds, List<String> closedTargetCodes) {
        BigDecimal income = BigDecimal.ZERO;
        for (ReceivableLine receivable : finance.listReceivables()) {
            if ("DELIVERY_BILL".equals(receivable.sourceType())) {
                income = income.add(receivable.amount());
            }
        }
        BigDecimal collected = BigDecimal.ZERO;
        for (ReceiptAllocationOperation operation : finance.listAllocationOperations()) {
            BigDecimal sign = ReceiptAllocationOperation.REVOKE.equals(operation.action())
                    ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            for (ReceiptAllocation line : operation.lines()) {
                collected = collected.add(line.amount().multiply(sign));
            }
        }
        BigDecimal labor = BigDecimal.ZERO;
        for (PayrollLine line : finance.listPayrollLines()) {
            if (line.confirmed()) {
                labor = labor.add(line.netAmount());
            }
        }
        BigDecimal paid = BigDecimal.ZERO;
        for (PayablePayment payment : finance.listPayablePayments()) {
            paid = paid.add(payment.amount());
        }
        List<String> awaitingBills = new ArrayList<>();
        for (String deliveryId : confirmedDeliveryIds == null ? List.<String>of() : confirmedDeliveryIds) {
            if (bills.findInitial(deliveryId).isEmpty()) {
                awaitingBills.add(deliveryId);
            }
        }
        List<String> awaitingSettlement = new ArrayList<>();
        for (String target : closedTargetCodes == null ? List.<String>of() : closedTargetCodes) {
            String prefix = "employee-target:" + target + ":";
            boolean settled = finance.listPayrollLines().stream()
                    .anyMatch(line -> line.sourceKey() != null && line.sourceKey().startsWith(prefix));
            if (!settled) {
                awaitingSettlement.add(target);
            }
        }
        boolean costIncomplete = !awaitingSettlement.isEmpty();
        BigDecimal uncollected = income.subtract(collected);
        return new FinanceContinuityView(
                income, collected, uncollected, labor, paid, labor.subtract(paid),
                List.copyOf(awaitingBills), List.copyOf(awaitingSettlement), costIncomplete,
                costIncomplete ? null : income.subtract(labor));
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
}
