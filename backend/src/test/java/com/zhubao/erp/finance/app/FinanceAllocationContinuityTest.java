package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.CustomerDeliveryBill;
import com.zhubao.erp.finance.domain.CustomerDeliveryBillKind;
import com.zhubao.erp.finance.domain.FinanceContinuityView;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceiptAllocationOperation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.infra.InMemoryCustomerDeliveryBillRepository;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceAllocationContinuityTest {

    @Test
    void 已收不计收入付款不计成本且负差额不截零() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        finance.saveReceivable(receivable("YS-1", "delivery-bill:cb1", "924.00"));
        finance.saveReceivable(receivable("YS-2", "delivery-bill:cb2", "-124.00"));
        finance.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt(
                "RC-1", 7L, "甲", "2026-10", new BigDecimal("900.00"), "2026-10-30", List.of(), "账务员", "2026-10-30T00:00:00"));
        finance.registerAllocationOperation(allocation("OP-1", ReceiptAllocationOperation.ALLOCATE, "900.00"));
        finance.savePayrollLine(payroll("WG-1", "employee-target:MD-1:a:抛光", "500.00"));
        finance.savePayable(payable("AP-1", "500.00"));
        finance.savePayablePayment(new PayablePayment("PAY-1", "AP-1", new BigDecimal("200.00"),
                "2026-10-30", "出纳", "2026-10-30T00:00:00"));
        bills.save(new CustomerDeliveryBill("CB" + "a".repeat(32), "e08-bill01", "CB" + "a".repeat(32),
                "FH-20261030-0001", 7L, CustomerDeliveryBillKind.INITIAL, new BigDecimal("800.00"), 1), List.of(), null);
        int receivables = finance.listReceivables().size();
        FinanceContinuityQuery query = new FinanceContinuityQuery(finance, bills);

        FinanceContinuityView view = query.read(List.of("FH-20261030-0001", "FH-20261030-0002"), List.of("MD-1"));

        assertThat(view.confirmedIncome()).isEqualByComparingTo("800.00");
        assertThat(view.collected()).isEqualByComparingTo("900.00");
        assertThat(view.uncollected()).isEqualByComparingTo("-100.00");
        assertThat(view.confirmedLaborCost()).isEqualByComparingTo("500.00");
        assertThat(view.paid()).isEqualByComparingTo("200.00");
        assertThat(view.unpaid()).isEqualByComparingTo("300.00");
        assertThat(view.recognizedProfit()).isEqualByComparingTo("300.00");
        assertThat(view.awaitingCustomerBill()).containsExactly("FH-20261030-0002");
        assertThat(view.awaitingEmployeeSettlement()).isEmpty();
        assertThat(view.costIncomplete()).isFalse();
        assertThat(query.unpaidLabor("AP-1")).isEqualByComparingTo("300.00");
        assertThat(finance.listReceivables()).hasSize(receivables);
        assertThat(finance.listPayablePayments()).hasSize(1);
    }

    @Test
    void 成本未齐时不把缺失工费当成零收益() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        finance.saveReceivable(receivable("YS-1", "delivery-bill:cb1", "800.00"));
        FinanceContinuityQuery query = new FinanceContinuityQuery(finance, new InMemoryCustomerDeliveryBillRepository());
        FinanceContinuityView view = query.read(List.of(), List.of("MD-1"));
        assertThat(view.costIncomplete()).isTrue();
        assertThat(view.recognizedProfit()).isNull();
        assertThat(view.confirmedLaborCost()).isEqualByComparingTo("0");
        assertThat(view.confirmedIncome()).isEqualByComparingTo("800.00");
    }

    private static ReceivableLine receivable(String code, String sourceKey, String amount) {
        return new ReceivableLine(code, 7L, "甲", "2026-10", "DELIVERY_BILL", sourceKey,
                "MD-1", "MD-1", "加工费", BigDecimal.ONE, com.zhubao.erp.finance.domain.ChargeUnit.JOB,
                new BigDecimal(amount), new BigDecimal(amount), "", "", "账务员", "2026-10-30T00:00:00");
    }

    private static ReceiptAllocationOperation allocation(String code, String action, String amount) {
        return new ReceiptAllocationOperation(code, "RC-1", 7L, action, "", "账务员", code,
                "2026-10-30T00:00:00", List.of(new ReceiptAllocation("YS-1", new BigDecimal(amount))));
    }

    private static PayrollLine payroll(String code, String sourceKey, String amount) {
        return new PayrollLine(code, "2026-10", "payee-a", "MD-1", "抛光", "MD-1", 7L,
                new BigDecimal("462.000"), com.zhubao.erp.finance.domain.ChargeUnit.PIECE, new BigDecimal("1.0000"),
                new BigDecimal(amount), BigDecimal.ZERO.setScale(2), new BigDecimal(amount), true,
                sourceKey, PayrollLine.POST_DELIVERY, "payee-a");
    }

    private static Payable payable(String code, String amount) {
        return new Payable(code, "工资", "2026-10", "payee-a", "MD-1", 7L, "MD-1",
                new BigDecimal(amount), BigDecimal.ZERO.setScale(2), "核算员", "2026-10-30T00:00:00");
    }
}
