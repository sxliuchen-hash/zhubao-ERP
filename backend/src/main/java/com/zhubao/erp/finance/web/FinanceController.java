package com.zhubao.erp.finance.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.finance.app.FinanceBillingService;
import com.zhubao.erp.finance.app.FinanceCostService;
import com.zhubao.erp.finance.app.FinanceDashboardService;
import com.zhubao.erp.finance.app.FinancePayrollService;
import com.zhubao.erp.finance.app.FinanceReadAuthorizer;
import com.zhubao.erp.finance.app.FinanceReportService;
import com.zhubao.erp.finance.domain.BillingRunResult;
import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.ConsumableStockView;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.CustomerStatement;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceDashboard;
import com.zhubao.erp.finance.domain.FinanceCommands.BillingRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.DepreciationRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.IssueConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.OutsourceBillCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PayablePaymentCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PayrollRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PurchaseConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.RecordReceiptCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReverseReceivableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveConsumableItemCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveFixedAssetCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SavePriceAgreementCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveWorkerRateCommand;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.OperatingStatement;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.domain.PayableView;
import com.zhubao.erp.finance.domain.PayrollGenerateResult;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.finance.domain.WorkerRate;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 经营核算接口。不做会计凭证，金额都能指回来料单、工序或应付。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceBillingService billingService;
    private final FinancePayrollService payrollService;
    private final FinanceCostService costService;
    private final FinanceReportService reportService;
    private final OperatorContextResolver operatorContextResolver;
    private final FinanceReadAuthorizer readAuthorizer;
    private final FinanceDashboardService dashboardService;

    public FinanceController(
            FinanceBillingService billingService,
            FinancePayrollService payrollService,
            FinanceCostService costService,
            FinanceReportService reportService,
            OperatorContextResolver operatorContextResolver
    ) {
        this(billingService, payrollService, costService, reportService, operatorContextResolver, new FinanceReadAuthorizer(), null);
    }

    @Autowired
    public FinanceController(
            FinanceBillingService billingService,
            FinancePayrollService payrollService,
            FinanceCostService costService,
            FinanceReportService reportService,
            OperatorContextResolver operatorContextResolver,
            FinanceReadAuthorizer readAuthorizer,
            FinanceDashboardService dashboardService
    ) {
        this.billingService = billingService;
        this.payrollService = payrollService;
        this.costService = costService;
        this.reportService = reportService;
        this.operatorContextResolver = operatorContextResolver;
        this.readAuthorizer = readAuthorizer;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/price-agreements")
    public ApiResponse<List<PriceAgreement>> agreements(
            @RequestParam(required = false) Long customerId,
            HttpServletRequest request
    ) {
        OperatorContext context = readContext(request);
        List<PriceAgreement> rows = billingService.agreements(customerId);
        if (customerId != null) {
            String name = rows.stream().map(PriceAgreement::customerName).filter(value -> !value.isBlank()).findFirst().orElse("");
            readAuthorizer.assertCustomer(context, customerId, name);
        }
        return ApiResponse.ok(readAuthorizer.visibleAgreements(context, rows));
    }

    @PostMapping("/price-agreements")
    public ApiResponse<PriceAgreement> saveAgreement(
            @Valid @RequestBody SavePriceAgreementCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(billingService.saveAgreement(command, operator(request)));
    }

    @PostMapping("/billing-runs")
    public ApiResponse<BillingRunResult> generate(@Valid @RequestBody BillingRunCommand command) {
        return ApiResponse.ok(billingService.generate(command));
    }

    @GetMapping("/unpriced")
    public ApiResponse<List<UnpricedItem>> unpriced(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long customerId,
            HttpServletRequest request
    ) {
        OperatorContext context = readContext(request);
        List<UnpricedItem> rows = billingService.unpriced(yearMonth, customerId);
        if (customerId != null) {
            String name = rows.stream().map(UnpricedItem::customerName).filter(value -> !value.isBlank()).findFirst().orElse("");
            readAuthorizer.assertCustomer(context, customerId, name);
        }
        return ApiResponse.ok(readAuthorizer.visibleUnpriced(context, rows));
    }

    @PostMapping("/receivable-reversals")
    public ApiResponse<ReceivableLine> reverse(
            @Valid @RequestBody ReverseReceivableCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(billingService.reverse(command, operator(request)));
    }

    @PostMapping("/receipts")
    public ApiResponse<CustomerReceipt> receive(
            @Valid @RequestBody RecordReceiptCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(billingService.receive(command, operator(request)));
    }

    @GetMapping("/statements")
    public ApiResponse<CustomerStatement> statement(
            @RequestParam long customerId,
            @RequestParam(required = false) String yearMonth,
            HttpServletRequest request
    ) {
        OperatorContext context = readContext(request);
        CustomerStatement statement = billingService.statement(customerId, yearMonth);
        readAuthorizer.assertCustomer(context, customerId, statement.customerName());
        return ApiResponse.ok(statement);
    }

    @GetMapping("/worker-rates")
    public ApiResponse<List<WorkerRate>> workerRates(HttpServletRequest request) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(payrollService.rates());
    }

    @PostMapping("/worker-rates")
    public ApiResponse<WorkerRate> saveWorkerRate(
            @Valid @RequestBody SaveWorkerRateCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(payrollService.saveRate(command, operator(request)));
    }

    @GetMapping("/payroll-lines")
    public ApiResponse<List<PayrollLine>> payrollLines(
            @RequestParam(required = false) String yearMonth,
            HttpServletRequest request
    ) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(payrollService.lines(yearMonth));
    }

    @PostMapping("/payroll-runs")
    public ApiResponse<PayrollGenerateResult> generatePayroll(@Valid @RequestBody PayrollRunCommand command) {
        return ApiResponse.ok(payrollService.generate(command));
    }

    @PostMapping("/payroll-runs/confirmations")
    public ApiResponse<PayrollRun> confirmPayroll(
            @Valid @RequestBody PayrollRunCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(payrollService.confirm(command, operator(request)));
    }

    @GetMapping("/consumable-items")
    public ApiResponse<List<ConsumableStockView>> consumables(HttpServletRequest request) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(costService.consumableStocks());
    }

    @GetMapping("/consumable-movements")
    public ApiResponse<List<ConsumableMovement>> movements(
            @RequestParam(required = false) String itemCode,
            HttpServletRequest request
    ) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(costService.movements(itemCode));
    }

    @PostMapping("/consumable-items")
    public ApiResponse<com.zhubao.erp.finance.domain.ConsumableItem> saveConsumable(
            @Valid @RequestBody SaveConsumableItemCommand command
    ) {
        return ApiResponse.ok(costService.saveItem(command));
    }

    @PostMapping("/consumable-purchases")
    public ApiResponse<ConsumableMovement> purchase(
            @Valid @RequestBody PurchaseConsumableCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(costService.purchase(command, operator(request)));
    }

    @PostMapping("/consumable-issues")
    public ApiResponse<ConsumableMovement> issue(
            @Valid @RequestBody IssueConsumableCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(costService.issue(command, operator(request)));
    }

    @GetMapping("/fixed-assets")
    public ApiResponse<List<FixedAsset>> assets(HttpServletRequest request) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(costService.assets());
    }

    @PostMapping("/fixed-assets")
    public ApiResponse<FixedAsset> saveAsset(
            @Valid @RequestBody SaveFixedAssetCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(costService.saveAsset(command, operator(request)));
    }

    @GetMapping("/depreciation-entries")
    public ApiResponse<List<DepreciationEntry>> depreciations(
            @RequestParam(required = false) String yearMonth,
            HttpServletRequest request
    ) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(costService.depreciations(yearMonth));
    }

    @PostMapping("/depreciation-runs")
    public ApiResponse<List<DepreciationEntry>> depreciate(@Valid @RequestBody DepreciationRunCommand command) {
        return ApiResponse.ok(costService.depreciate(command));
    }

    @PostMapping("/outsource-bills")
    public ApiResponse<Payable> outsource(
            @Valid @RequestBody OutsourceBillCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(costService.outsource(command, operator(request)));
    }

    @GetMapping("/payables")
    public ApiResponse<List<PayableView>> payables(
            @RequestParam(required = false) String yearMonth,
            HttpServletRequest request
    ) {
        readAuthorizer.assertCompany(readContext(request));
        return ApiResponse.ok(reportService.payables(yearMonth));
    }

    @PostMapping("/payable-payments")
    public ApiResponse<PayablePayment> pay(
            @Valid @RequestBody PayablePaymentCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(costService.pay(command, operator(request)));
    }

    @GetMapping("/operating-statement")
    public ApiResponse<OperatingStatement> operating(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long customerId,
            HttpServletRequest request
    ) {
        OperatorContext context = readContext(request);
        if (customerId == null) {
            readAuthorizer.assertCompany(context);
        } else {
            CustomerStatement statement = billingService.statement(customerId, yearMonth);
            readAuthorizer.assertCustomer(context, customerId, statement.customerName());
        }
        return ApiResponse.ok(reportService.operating(yearMonth, customerId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<FinanceDashboard> dashboard(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long customerId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(dashboardService.dashboard(readContext(request), yearMonth, customerId));
    }

    private OperatorContext readContext(HttpServletRequest request) {
        readAuthorizer.assertSignedIn(request);
        return operatorContextResolver.resolve(request);
    }

    private String operator(HttpServletRequest request) {
        return operatorContextResolver.resolve(request).operator();
    }
}
