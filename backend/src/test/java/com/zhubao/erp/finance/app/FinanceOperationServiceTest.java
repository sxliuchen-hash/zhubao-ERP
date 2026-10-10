package com.zhubao.erp.finance.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.infra.InMemoryApprovalTaskRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceCommands.BillingRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.DepreciationRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.IssueConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.OutsourceBillCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PayablePaymentCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PayrollRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PriceLineCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PurchaseConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.RecordReceiptCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReverseReceivableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveConsumableItemCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveFixedAssetCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SavePriceAgreementCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveWorkerRateCommand;
import com.zhubao.erp.finance.domain.OperatingStatement;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceOperationServiceTest {

    private InMemoryFinanceRepository finance;
    private InMemoryBusinessDocumentRepository documents;
    private StockLedgerService stock;
    private InMemoryApprovalTaskRepository approvals;
    private FinanceBillingService billing;
    private FinancePayrollService payroll;
    private FinanceCostService cost;
    private FinanceReportService report;

    @BeforeEach
    void setUp() {
        finance = new InMemoryFinanceRepository();
        documents = new InMemoryBusinessDocumentRepository();
        stock = new StockLedgerService(new InMemoryStockLedgerRepository());
        approvals = new InMemoryApprovalTaskRepository();
        billing = new FinanceBillingService(finance, documents, stock);
        payroll = new FinancePayrollService(finance, documents, new ApprovalService(approvals));
        cost = new FinanceCostService(finance);
        report = new FinanceReportService(finance, billing);
    }

    @Test
    void billsInboundWeightOnceAndKeepsThePriceSnapshot() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-08", "2", "10.500"));
        billing.saveAgreement(agreement(1001L, "WEIGHT", "2026-10", "", new PriceLineCommand("", "GRAM", new BigDecimal("12.5"))), "财务");

        var first = billing.generate(new BillingRunCommand("2026-10", 1001L));
        var second = billing.generate(new BillingRunCommand("2026-10", 1001L));

        assertThat(first.createdCount()).isEqualTo(1);
        assertThat(first.createdLines().get(0).amount()).isEqualByComparingTo("131.25");
        assertThat(first.createdLines().get(0).motherOrderCode()).isEqualTo("MD-1");
        assertThat(first.createdLines().get(0).unitPrice()).isEqualByComparingTo("12.5000");
        assertThat(second.createdCount()).isZero();

        billing.saveAgreement(agreement(1001L, "WEIGHT", "2026-11", "", new PriceLineCommand("", "GRAM", new BigDecimal("20"))), "财务");
        assertThat(billing.statement(1001L, "2026-10").lines().get(0).unitPrice()).isEqualByComparingTo("12.5000");
    }

    @Test
    void billsPieceAndProcessSeparatelyAndSkipsRework() {
        documents.saveMotherOrder(mother("MD-P", 1002L, "雅辰", "2026-10-02", "3", "1"));
        billing.saveAgreement(agreement(1002L, "PIECE", "2026-10", "", new PriceLineCommand("", "PIECE", new BigDecimal("8"))), "财务");
        assertThat(billing.generate(new BillingRunCommand("2026-10", null)).createdLines())
                .singleElement()
                .satisfies(line -> assertThat(line.amount()).isEqualByComparingTo("24.00"));

        documents.saveMotherOrder(mother("MD-S", 1003L, "锦瑞", "2026-10-03", "1", "2"));
        documents.saveChildWorkOrder(child("ZD-1", "MD-S", 1003L, "1", "2.000"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-1", "雕刻", "王强", "STK-1", "2026-10-09T09:00:00"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("FG-ZD-1", "抛光", "王强", "STK-2", "2026-10-09T11:00:00"));
        billing.saveAgreement(new SavePriceAgreementCommand(
                1003L,
                "锦瑞",
                "PROCESS",
                "2026-10",
                "",
                List.of(new PriceLineCommand("雕刻", "JOB", new BigDecimal("30")))
        ), "财务");

        var result = billing.generate(new BillingRunCommand("2026-10", 1003L));
        assertThat(result.createdLines()).singleElement().satisfies(line -> {
            assertThat(line.sourceType()).isEqualTo("PROCESS");
            assertThat(line.workOrderCode()).isEqualTo("ZD-1");
            assertThat(line.processName()).isEqualTo("雕刻");
            assertThat(line.amount()).isEqualByComparingTo("30.00");
        });
        assertThat(result.unpriced()).isEmpty();
    }

    @Test
    void leavesUnpricedOrdersUntilAnAgreementExists() {
        documents.saveMotherOrder(mother("MD-U", 1004L, "未定价", "2026-10-04", "1", "1"));

        assertThat(billing.unpriced("2026-10", 1004L))
                .singleElement()
                .satisfies(item -> assertThat(item.reason()).contains("没有生效的客户协议"));
        assertThat(finance.listReceivables()).isEmpty();

        billing.saveAgreement(agreement(1004L, "PIECE", "2026-10", "", new PriceLineCommand("", "PIECE", new BigDecimal("6"))), "财务");
        assertThat(billing.generate(new BillingRunCommand("2026-10", 1004L)).createdCount()).isEqualTo(1);
        assertThat(billing.unpriced("2026-10", 1004L)).isEmpty();
    }

    @Test
    void allocatesReceiptsAndReversesWithoutRewritingTheOriginalLine() {
        documents.saveMotherOrder(mother("MD-R", 1005L, "收款客户", "2026-10-05", "2", "1"));
        stock.transfer(new StockTransferCommand(
                "inbound",
                "MD-R",
                null,
                new StockDimension(OwnerType.CUSTOMER, 1005L, 1005L, "MD-R", null, "MAT", "RAW", StockStatus.RAW_WAITING),
                new BigDecimal("2"),
                new BigDecimal("1.5"),
                "结存"
        ));
        billing.saveAgreement(agreement(1005L, "PIECE", "2026-10", "", new PriceLineCommand("", "PIECE", new BigDecimal("10"))), "财务");
        var line = billing.generate(new BillingRunCommand("2026-10", 1005L)).createdLines().get(0);
        billing.receive(new RecordReceiptCommand(
                1005L,
                "收款客户",
                "2026-10-20",
                new BigDecimal("25"),
                List.of(new ReceiptAllocationCommand(line.code(), new BigDecimal("15")))
        ), "财务");
        String originalAmount = line.amount().toPlainString();
        billing.reverse(new ReverseReceivableCommand(line.code(), new BigDecimal("5"), "退货冲减"), "财务");

        var statement = billing.statement(1005L, "");
        assertThat(finance.findReceivableByCode(line.code()).orElseThrow().amount().toPlainString()).isEqualTo(originalAmount);
        assertThat(statement.receivableAmount()).isEqualByComparingTo("15.00");
        assertThat(statement.receivedAmount()).isEqualByComparingTo("15.00");
        assertThat(statement.outstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(statement.prepaymentAmount()).isEqualByComparingTo("10.00");
        assertThat(statement.stockQuantity()).isEqualByComparingTo("2.000");
        assertThat(statement.stockWeight()).isEqualByComparingTo("1.500");
        assertThat(statement.stockNote()).contains("当前结存");
    }

    @Test
    void confirmsPayrollOnceAndCapsLossDeduction() {
        documents.saveMotherOrder(mother("MD-W", 1006L, "工资客户", "2026-10-01", "2", "1"));
        documents.saveChildWorkOrder(child("ZD-W", "MD-W", 1006L, "2", "1"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-W", "镶嵌", "赵敏", "STK-W", "2026-10-11T08:00:00"));
        approvals.save(new ApprovalTask(
                "AP-W",
                "LOSS_APPROVAL",
                "ZD-W",
                "Loss approval for ZD-W / 镶嵌",
                ApprovalStatus.APPROVED,
                "赵敏",
                "组长",
                "WORKSHOP_LEADER",
                "red",
                "APPROVED",
                "lossDeduction=50.00; trialWage=0",
                "",
                false,
                "2026-10-11T09:00:00",
                "2026-10-11T09:10:00",
                "组长",
                "同意",
                true
        ));
        payroll.saveRate(new SaveWorkerRateCommand("镶嵌", "PIECE", new BigDecimal("10"), "2026-10", ""), "财务");

        var generated = payroll.generate(new PayrollRunCommand("2026-10"));
        assertThat(generated.lines()).singleElement().satisfies(line -> {
            assertThat(line.grossAmount()).isEqualByComparingTo("20.00");
            assertThat(line.lossDeduction()).isEqualByComparingTo("20.00");
            assertThat(line.netAmount()).isEqualByComparingTo("0.00");
            assertThat(line.confirmed()).isFalse();
        });

        payroll.confirm(new PayrollRunCommand("2026-10"), "财务");
        assertThat(payroll.lines("2026-10")).allMatch(line -> line.confirmed());
        assertThat(report.payables("2026-10")).singleElement().satisfies(payable -> {
            assertThat(payable.kind()).isEqualTo("PAYROLL");
            assertThat(payable.payee()).isEqualTo("赵敏");
            assertThat(payable.unpaidAmount()).isEqualByComparingTo("0.00");
        });
        assertThatThrownBy(() -> payroll.generate(new PayrollRunCommand("2026-10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已锁定");
    }

    @Test
    void tracksConsumablesAssetsAndMonthlyOperatingResult() {
        documents.saveMotherOrder(mother("MD-C", 1007L, "经营客户", "2026-10-06", "1", "1"));
        billing.saveAgreement(agreement(1007L, "PIECE", "2026-10", "", new PriceLineCommand("", "PIECE", new BigDecimal("100"))), "财务");
        billing.generate(new BillingRunCommand("2026-10", 1007L));
        documents.saveChildWorkOrder(child("ZD-C", "MD-C", 1007L, "1", "1"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-C", "抛光", "陈刚", "STK-C", "2026-10-12T08:00:00"));
        payroll.saveRate(new SaveWorkerRateCommand("抛光", "JOB", new BigDecimal("40"), "2026-10", ""), "财务");
        payroll.confirm(new PayrollRunCommand("2026-10"), "财务");

        cost.saveItem(new SaveConsumableItemCommand("GLUE", "胶", "支"));
        var purchase = cost.purchase(new PurchaseConsumableCommand("GLUE", new BigDecimal("10"), new BigDecimal("30"), "胶行", "2026-10"), "财务");
        cost.issue(new IssueConsumableCommand("GLUE", new BigDecimal("4"), 1007L, "2026-10"), "财务");
        assertThatThrownBy(() -> cost.issue(new IssueConsumableCommand("GLUE", new BigDecimal("7"), null, "2026-10"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不足");
        assertThat(cost.consumableStocks().get(0).onHandQuantity()).isEqualByComparingTo("6.000");
        assertThat(cost.consumableStocks().get(0).onHandAmount()).isEqualByComparingTo("18.00");

        cost.saveAsset(new SaveFixedAssetCommand("抛光机", "设备", new BigDecimal("1000"), new BigDecimal("0"), "2026-10", 3), "财务");
        assertThat(cost.depreciate(new DepreciationRunCommand("2026-10"))).singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("333.33"));
        assertThat(cost.depreciate(new DepreciationRunCommand("2026-10"))).isEmpty();
        assertThat(cost.depreciate(new DepreciationRunCommand("2026-12"))).singleElement()
                .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("333.34"));

        var outsource = cost.outsource(new OutsourceBillCommand("外协厂", new BigDecimal("15"), "2026-10", 1007L, "MD-C"), "财务");
        cost.pay(new PayablePaymentCommand(purchase.payableCode(), new BigDecimal("10"), "2026-10-21"), "财务");

        OperatingStatement customer = report.operating("2026-10", 1007L);
        assertThat(customer.revenue()).isEqualByComparingTo("100.00");
        assertThat(customer.wageCost()).isEqualByComparingTo("40.00");
        assertThat(customer.consumableCost()).isEqualByComparingTo("12.00");
        assertThat(customer.depreciationCost()).isEqualByComparingTo("0.00");
        assertThat(customer.outsourceCost()).isEqualByComparingTo("15.00");
        assertThat(customer.result()).isEqualByComparingTo("33.00");
        assertThat(customer.gaps()).isEmpty();

        OperatingStatement company = report.operating("2026-10", null);
        assertThat(company.depreciationCost()).isEqualByComparingTo("333.33");
        assertThat(company.result()).isEqualByComparingTo("-300.33");
        assertThat(report.payables("2026-10"))
                .anySatisfy(payable -> {
                    assertThat(payable.code()).isEqualTo(outsource.code());
                    assertThat(payable.kind()).isEqualTo("OUTSOURCE");
                    assertThat(payable.unpaidAmount()).isEqualByComparingTo("15.00");
                })
                .anySatisfy(payable -> {
                    assertThat(payable.code()).isEqualTo(purchase.payableCode());
                    assertThat(payable.paidAmount()).isEqualByComparingTo("10.00");
                    assertThat(payable.unpaidAmount()).isEqualByComparingTo("20.00");
                });
    }

    @Test
    void reportsMissingPayrollAndDepreciationAsGaps() {
        cost.saveAsset(new SaveFixedAssetCommand("空压机", "设备", new BigDecimal("500"), new BigDecimal("100"), "2026-09", 12), "财务");
        OperatingStatement statement = report.operating("2026-10", null);
        assertThat(statement.revenue()).isEqualByComparingTo("0.00");
        assertThat(statement.gaps()).contains("工资未确认，成本按 0", "折旧未生成，成本按 0");
        assertThat(PricingMode.WEIGHT.name()).isEqualTo("WEIGHT");
        assertThat(ChargeUnit.GRAM.name()).isEqualTo("GRAM");
    }

    private SavePriceAgreementCommand agreement(
            long customerId,
            String mode,
            String from,
            String to,
            PriceLineCommand line
    ) {
        return new SavePriceAgreementCommand(customerId, "客户" + customerId, mode, from, to, List.of(line));
    }

    private MotherOrderRecord mother(
            String code,
            Long customerId,
            String customerName,
            String inboundDate,
            String quantity,
            String weight
    ) {
        return new MotherOrderRecord(
                code,
                customerId,
                customerId,
                "MAT",
                inboundDate,
                "",
                "",
                "",
                "",
                "",
                "货",
                "CUSTOMER",
                true,
                "RAW",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "RAW_WAITING",
                "",
                "",
                null,
                "",
                "收货",
                "",
                "",
                "CONFIRMED",
                inboundDate + "T08:00:00",
                "抛光",
                "2026-10-31",
                customerName
        );
    }

    private ChildWorkOrderRecord child(String code, String motherCode, Long customerId, String quantity, String weight) {
        return new ChildWorkOrderRecord(
                code,
                motherCode,
                customerId,
                "客户",
                "抛光",
                "2026-10-31",
                "MAT",
                "货",
                new BigDecimal(quantity),
                "件",
                new BigDecimal(weight),
                "RAW",
                "雕刻,镶嵌,抛光",
                "V1",
                "",
                "收货",
                "2026-10-08T08:00:00"
        );
    }
}
