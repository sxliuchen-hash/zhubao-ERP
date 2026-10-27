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
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.domain.ReceivableLine;
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
    void agreementAndBillingGenerationAreRejected() {
        documents.saveMotherOrder(mother("MD-1", 1001L, "恒信珠宝", "2026-10-08", "2", "10.500"));
        assertThatThrownBy(() -> billing.saveAgreement(agreement(1001L, "WEIGHT", "2026-10", "", new PriceLineCommand("", "GRAM", new BigDecimal("12.5"))), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("协议已停止维护");
        assertThatThrownBy(() -> billing.generate(new BillingRunCommand("2026-10", 1001L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停止按收货或交工生成加工费");
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(billing.unpriced("2026-10", 1001L)).isEmpty();
    }

    @Test
    void payrollRebuildIsRejected() {
        assertThatThrownBy(() -> payroll.saveRate(new SaveWorkerRateCommand("镶嵌", "PIECE", new BigDecimal("10"), "2026-10", ""), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工价已停止维护");
        assertThatThrownBy(() -> payroll.generate(new PayrollRunCommand("2026-10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停止按交工");
        assertThatThrownBy(() -> payroll.confirm(new PayrollRunCommand("2026-10"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停止整月确认");
        assertThat(finance.listPayrollLines()).isEmpty();
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
        ReceivableLine line = new ReceivableLine(
                "FIN-R", 1005L, "收款客户", "2026-10", "MANUAL", "MANUAL|FIN-R", "MD-R", "MD-R", "",
                new BigDecimal("2"), ChargeUnit.PIECE, new BigDecimal("10.0000"), new BigDecimal("20.00"),
                "", "", "财务", "2026-10-05T00:00:00");
        finance.saveReceivable(line);
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
    void monthlyPayrollRebuildCannotDeductFromApprovalText() {
        assertThatThrownBy(() -> payroll.saveRate(new SaveWorkerRateCommand("镶嵌", "PIECE", new BigDecimal("10"), "2026-10", ""), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工价已停止维护");
        assertThatThrownBy(() -> payroll.generate(new PayrollRunCommand("2026-10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停止按交工");
        assertThat(finance.listPayables()).isEmpty();
    }

    @Test
    void tracksConsumablesAssetsAndMonthlyOperatingResult() {
        documents.saveMotherOrder(mother("MD-C", 1007L, "经营客户", "2026-10-06", "1", "1"));
        finance.saveReceivable(new ReceivableLine(
                "FIN-C", 1007L, "经营客户", "2026-10", "MANUAL", "MANUAL|FIN-C", "MD-C", "MD-C", "",
                BigDecimal.ONE, ChargeUnit.JOB, new BigDecimal("100.0000"), new BigDecimal("100.00"),
                "", "", "财务", "2026-10-06T00:00:00"));
        documents.saveChildWorkOrder(child("ZD-C", "MD-C", 1007L, "1", "1"));
        documents.saveProcessStationHandoff(new ProcessStationHandoff("ZD-C", "抛光", "陈刚", "STK-C", "2026-10-12T08:00:00"));
        finance.savePayrollLine(new PayrollLine(
                "WG-C", "2026-10", "陈刚", "ZD-C", "抛光", "MD-C", 1007L, BigDecimal.ONE, ChargeUnit.JOB,
                new BigDecimal("40.0000"), new BigDecimal("40.00"), BigDecimal.ZERO, new BigDecimal("40.00"),
                true, "MANUAL|WG-C"));
        finance.savePayrollRun(new PayrollRun("2026-10", true, "财务", "2026-10-12T00:00:00"));

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
        assertThat(statement.gaps()).contains("折旧未生成，成本按 0");
        assertThat(statement.gaps()).noneMatch(gap -> gap.contains("工资未确认"));
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
