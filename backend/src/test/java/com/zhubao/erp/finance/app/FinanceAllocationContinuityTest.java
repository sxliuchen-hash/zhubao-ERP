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
        String billId = "CB" + "a".repeat(32);
        String adjustmentId = "CB" + "b".repeat(32);
        finance.saveReceivable(receivable("YS-1", "delivery-bill:" + billId, "924.00"));
        finance.saveReceivable(receivable("YS-2", "delivery-bill:" + adjustmentId, "-124.00"));
        finance.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt(
                "RC-1", 7L, "甲", "2026-10", new BigDecimal("900.00"), "2026-10-30", List.of(), "账务员", "2026-10-30T00:00:00"));
        finance.registerAllocationOperation(allocation("OP-1", ReceiptAllocationOperation.ALLOCATE, "900.00"));
        finance.savePayrollLine(payroll("WG-1", "employee-target:MD-1:a:抛光", "500.00"));
        finance.savePayable(payable("AP-1", "500.00"));
        finance.savePayablePayment(new PayablePayment("PAY-1", "AP-1", new BigDecimal("200.00"),
                "2026-10-30", "出纳", "2026-10-30T00:00:00"));
        bills.save(new CustomerDeliveryBill(billId, "e08-bill01", billId,
                "FH-20261030-0001", 7L, CustomerDeliveryBillKind.INITIAL, new BigDecimal("924.00"), 1), List.of(), null);
        bills.save(new CustomerDeliveryBill(adjustmentId, "e08-adj001", billId,
                "FH-20261030-0001", 7L, CustomerDeliveryBillKind.ADJUSTMENT, new BigDecimal("800.00"), 2), List.of(), null);
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
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        String billId = "CB" + "c".repeat(32);
        finance.saveReceivable(receivable("YS-1", "delivery-bill:" + billId, "800.00"));
        bills.save(new CustomerDeliveryBill(billId, "e08-bill02", billId,
                "FH-20261030-0001", 7L, CustomerDeliveryBillKind.INITIAL, new BigDecimal("800.00"), 1), List.of(), null);
        FinanceContinuityQuery query = new FinanceContinuityQuery(finance, bills);
        FinanceContinuityView view = query.read(List.of("FH-20261030-0001"), List.of("MD-1"));
        assertThat(view.costIncomplete()).isTrue();
        assertThat(view.recognizedProfit()).isNull();
        assertThat(view.confirmedLaborCost()).isEqualByComparingTo("0");
        assertThat(view.confirmedIncome()).isEqualByComparingTo("800.00");
    }

    @Test
    void 供应商付款和无关收款不进本次工资与新账() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        String billId = "CB" + "d".repeat(32);
        finance.saveReceivable(receivable("YS-NEW", "delivery-bill:" + billId, "100.00"));
        finance.saveReceivable(new ReceivableLine("YS-OLD", 7L, "甲", "2026-10", "ORDER_SETTLEMENT", "old-settlement",
                "MD-1", "MD-1", "旧账", BigDecimal.ONE, com.zhubao.erp.finance.domain.ChargeUnit.JOB,
                new BigDecimal("50.00"), new BigDecimal("50.00"), "", "", "账务员", "2026-10-30T00:00:00"));
        finance.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt(
                "RC-OLD", 7L, "甲", "2026-10", new BigDecimal("50.00"), "2026-10-30", List.of(), "账务员", "2026-10-30T00:00:00"));
        finance.registerAllocationOperation(new ReceiptAllocationOperation(
                "OP-OLD", "RC-OLD", 7L, ReceiptAllocationOperation.ALLOCATE, "", "账务员", "OP-OLD",
                "2026-10-30T00:00:00", List.of(new ReceiptAllocation("YS-OLD", new BigDecimal("50.00")))));
        bills.save(new CustomerDeliveryBill(billId, "e08-bill03", billId,
                "FH-20261030-0003", 7L, CustomerDeliveryBillKind.INITIAL, new BigDecimal("100.00"), 1), List.of(), null);
        finance.savePayrollLine(payroll("WG-1", "employee-target:MD-1:a:抛光", "20.00"));
        finance.savePayable(payable("AP-W", "20.00"));
        finance.savePayable(new Payable("AP-S", "采购", "2026-10", "供应商", "PO-1", null, "",
                new BigDecimal("100.00"), BigDecimal.ZERO, "库管", "2026-10-30T00:00:00"));
        finance.savePayablePayment(new PayablePayment("PAY-S", "AP-S", new BigDecimal("100.00"),
                "2026-10-30", "出纳", "2026-10-30T00:00:00"));
        finance.savePayablePayment(new PayablePayment("PAY-W", "AP-W", new BigDecimal("5.00"),
                "2026-10-30", "出纳", "2026-10-30T00:00:00"));
        FinanceContinuityQuery query = new FinanceContinuityQuery(finance, bills);

        FinanceContinuityView view = query.read(List.of("FH-20261030-0003"), List.of("MD-1"));

        assertThat(view.confirmedIncome()).isEqualByComparingTo("100.00");
        assertThat(view.collected()).isEqualByComparingTo("0");
        assertThat(view.uncollected()).isEqualByComparingTo("100.00");
        assertThat(view.confirmedLaborCost()).isEqualByComparingTo("20.00");
        assertThat(view.paid()).isEqualByComparingTo("5.00");
        assertThat(view.unpaid()).isEqualByComparingTo("15.00");
        assertThat(view.recognizedProfit()).isEqualByComparingTo("80.00");
        assertThat(query.unpaidLabor("AP-S")).isEqualByComparingTo("0");
        assertThat(query.unpaidLabor("AP-W")).isEqualByComparingTo("15.00");
        assertThat(query.read(List.of(), List.of("MD-1")).confirmedIncome()).isEqualByComparingTo("0");
    }

    @Test
    void 本次覆盖的旧账和收款不丢失且空核算范围不显示利润() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        String billId = "CB" + "e".repeat(32);
        bills.save(new CustomerDeliveryBill(billId, "rf17-covered", billId, "FH-20261030-1701", 7L,
                CustomerDeliveryBillKind.INITIAL, new BigDecimal("100"), 1), List.of(), null);
        finance.saveReceivable(legacy("YS-COVERED"));
        finance.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt("RC-OLD", 7L, "甲", "2026-10",
                new BigDecimal("100"), "2026-10-31", List.of(), "账务员", "2026-10-31T00:00:00"));
        bills.saveCoverage(new com.zhubao.erp.finance.domain.CustomerBillCoverage(billId, "YS-COVERED", new BigDecimal("100")), new BigDecimal("100"), null);
        finance.registerAllocationOperation(oldAllocation("ALLOC-COVERED", "YS-COVERED", "100"));
        var query = new FinanceContinuityQuery(finance, bills);
        var view = query.read(List.of("FH-20261030-1701"), List.of());
        assertThat(view.confirmedIncome()).isEqualByComparingTo("100");
        assertThat(view.collected()).isEqualByComparingTo("100");
        assertThat(view.uncollected()).isEqualByComparingTo("0");
        assertThat(view.costIncomplete()).isTrue();
        assertThat(view.recognizedProfit()).isNull();
        assertThat(view.awaitingReceiptCoverage()).isEmpty();
    }

    @Test
    void 分批覆盖遇部分旧收款须待核不擅自比例分摊() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryCustomerDeliveryBillRepository bills = new InMemoryCustomerDeliveryBillRepository();
        finance.saveReceivable(legacy("YS-PARTIAL"));
        finance.saveReceipt(new com.zhubao.erp.finance.domain.CustomerReceipt("RC-OLD", 7L, "甲", "2026-10",
                new BigDecimal("50"), "2026-10-31", List.of(), "账务员", "2026-10-31T00:00:00"));
        for (String amount : List.of("40", "60")) {
            String billId = "CB" + amount.repeat(16);
            bills.save(new CustomerDeliveryBill(billId, "rf17-part-" + amount, billId, "FH-20261030-00" + amount, 7L,
                    CustomerDeliveryBillKind.INITIAL, new BigDecimal(amount), 1), List.of(), null);
            bills.saveCoverage(new com.zhubao.erp.finance.domain.CustomerBillCoverage(billId, "YS-PARTIAL", new BigDecimal(amount)), new BigDecimal("100"), null);
        }
        finance.registerAllocationOperation(oldAllocation("ALLOC-PARTIAL", "YS-PARTIAL", "50"));
        var query = new FinanceContinuityQuery(finance, bills);
        var partial = query.read(List.of("FH-20261030-0040"), List.of());
        assertThat(partial.confirmedIncome()).isEqualByComparingTo("40");
        assertThat(partial.collected()).isNull();
        assertThat(partial.uncollected()).isNull();
        assertThat(partial.awaitingReceiptCoverage()).containsExactly("YS-PARTIAL");
        var all = query.read(List.of("FH-20261030-0040", "FH-20261030-0060"), List.of());
        assertThat(all.confirmedIncome()).isEqualByComparingTo("100");
        assertThat(all.collected()).isEqualByComparingTo("50");
        assertThat(all.uncollected()).isEqualByComparingTo("50");
        finance.registerAllocationOperation(new ReceiptAllocationOperation("REVOKE-OLD", "RC-OLD", 7L,
                ReceiptAllocationOperation.REVOKE, "更正", "账务员", "REVOKE-OLD", "2026-10-31T00:00:00",
                List.of(new ReceiptAllocation("YS-PARTIAL", new BigDecimal("20")))));
        assertThat(query.read(List.of("FH-20261030-0040", "FH-20261030-0060"), List.of()).collected()).isEqualByComparingTo("30");
    }

    private static ReceivableLine legacy(String code) {
        return new ReceivableLine(code, 7L, "甲", "2026-10", "ORDER_SETTLEMENT", "old-" + code, "MD-1", "MD-1",
                "旧加工费", BigDecimal.ONE, com.zhubao.erp.finance.domain.ChargeUnit.JOB, new BigDecimal("100"), new BigDecimal("100"), "", "", "账务员", "2026-10-31T00:00:00");
    }

    private static ReceiptAllocationOperation oldAllocation(String code, String receivable, String amount) {
        return new ReceiptAllocationOperation(code, "RC-OLD", 7L, ReceiptAllocationOperation.ALLOCATE, "", "账务员", code,
                "2026-10-31T00:00:00", List.of(new ReceiptAllocation(receivable, new BigDecimal(amount))));
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
