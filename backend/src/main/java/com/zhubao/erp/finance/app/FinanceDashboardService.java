package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.CurrentBalances;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceCapability;
import com.zhubao.erp.finance.domain.FinanceDashboard;
import com.zhubao.erp.finance.domain.FinanceDashboardScope;
import com.zhubao.erp.finance.domain.FinanceDetailPage;
import com.zhubao.erp.finance.domain.FinanceDetailPage.FinanceDetailRow;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FinanceTrendPoint;
import com.zhubao.erp.finance.domain.OperatingStatement;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.ReceivableRanking;
import com.zhubao.erp.finance.domain.ReceivableRanking.ReceivableRankRow;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 只读汇总选择月份和向前五个月的经营结果。不生成账单、工资、折旧或核销。
 */
@Service
public class FinanceDashboardService {

    private static final int RANK_LIMIT = 20;
    private static final int DETAIL_SIZE_LIMIT = 100;

    private final FinanceReportService reportService;
    private final FinanceBillingService billingService;
    private final FinanceReadAuthorizer readAuthorizer;
    private final FinanceRepository finance;
    private final PermissionGuard permissionGuard;

    public FinanceDashboardService(
            FinanceReportService reportService,
            FinanceBillingService billingService,
            FinanceReadAuthorizer readAuthorizer,
            FinanceRepository finance,
            PermissionGuard permissionGuard
    ) {
        this.reportService = reportService;
        this.billingService = billingService;
        this.readAuthorizer = readAuthorizer;
        this.finance = finance;
        this.permissionGuard = permissionGuard;
    }

    public FinanceDashboard dashboard(OperatorContext context, String yearMonth, Long customerId) {
        YearMonth selected = FinanceTexts.month(yearMonth);
        FinanceDashboardScope scope = resolveScope(context, customerId);
        List<FinanceTrendPoint> trend = new ArrayList<>();
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth point = selected.minusMonths(offset);
            trend.add(new FinanceTrendPoint(
                    point.toString(),
                    point.equals(YearMonth.now()),
                    aggregate(scope, point.toString())
            ));
        }
        OperatingStatement operating = trend.get(trend.size() - 1).operating();
        String asOf = FinanceTexts.now();
        CurrentBalances balances = currentBalances(scope, asOf);
        ReceivableRanking ranking = receivableRanking(scope);
        return new FinanceDashboard(
                selected.toString(),
                asOf,
                scope,
                operating,
                trend,
                balances,
                ranking,
                List.of(
                        new FinanceCapability("operating", true, ""),
                        new FinanceCapability("trend", true, "含选择月在内的六个月。当前自然月只截至本次查询，不与完整月计算增幅"),
                        new FinanceCapability("currentBalances", true, balanceReason(balances)),
                        new FinanceCapability("receivableRanking", true, ranking.truncated()
                                ? "只返回未收不为 0 的前 " + RANK_LIMIT + " 名"
                                : "只含未收不为 0 的客户，按未收从高到低")
                )
        );
    }

    /**
     * 只读明细。收入和成本跟随月份与范围；未收和应付使用当前时点，不改成所选月份的小计。
     */
    public FinanceDetailPage details(
            OperatorContext context,
            String kind,
            String yearMonth,
            Long customerId,
            int page,
            int size
    ) {
        if (page < 1 || size < 1 || size > DETAIL_SIZE_LIMIT) {
            throw new BusinessException("明细分页超出可读取范围");
        }
        String normalized = kind == null ? "" : kind.trim().toUpperCase();
        boolean periodKind = "REVENUE".equals(normalized) || "COST".equals(normalized);
        if (periodKind && (yearMonth == null || yearMonth.isBlank())) {
            throw new BusinessException("收入和成本明细必须指定月份");
        }
        if (!periodKind && !"RECEIVABLE".equals(normalized) && !"PAYABLE".equals(normalized)) {
            throw new BusinessException("明细种类只接受 REVENUE、COST、RECEIVABLE、PAYABLE");
        }
        FinanceDashboardScope scope = resolveScope(context, customerId);
        String asOf = FinanceTexts.now();
        String month = periodKind ? FinanceTexts.month(yearMonth).toString() : "";
        List<FinanceDetailRow> all = switch (normalized) {
            case "REVENUE" -> revenueRows(scope, month);
            case "COST" -> costRows(scope, month);
            case "RECEIVABLE" -> receivableRows(scope);
            default -> payableRows(scope);
        };
        BigDecimal totalAmount = FinanceTexts.ZERO;
        for (FinanceDetailRow row : all) {
            totalAmount = totalAmount.add(row.amount());
        }
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new FinanceDetailPage(
                normalized,
                asOf,
                month,
                page,
                size,
                all.size(),
                FinanceTexts.money(totalAmount),
                all.subList(from, to)
        );
    }

    private FinanceDashboardScope resolveScope(OperatorContext context, Long customerId) {
        if (customerId != null) {
            String name = customerName(customerId);
            readAuthorizer.assertCustomer(context, customerId, name);
            return new FinanceDashboardScope(
                    "CUSTOMER",
                    List.of(customerId),
                    "只含该客户可归集的收入和成本，不含公司折旧和未分配成本"
            );
        }
        if (context.dataScope() == DataScope.ALL) {
            permissionGuard.assertAllowed(PermissionCode.FINANCE_COMPANY_READ, context);
            readAuthorizer.assertCompany(context);
            return new FinanceDashboardScope(
                    "COMPANY",
                    List.of(),
                    "全公司，含折旧和未能按客户归集的成本"
            );
        }
        if (context.customerScope().isEmpty()) {
            readAuthorizer.assertCustomer(context, 0L, "");
        }
        List<Long> ids = customerIdsInScope(context);
        return new FinanceDashboardScope(
                "CUSTOMERS",
                ids,
                "授权客户集合内可归集的收入和成本，不含公司折旧，也不包含未授权客户"
        );
    }

    private CurrentBalances currentBalances(FinanceDashboardScope scope, String asOf) {
        BigDecimal outstanding = FinanceTexts.ZERO;
        for (Long customerId : balanceCustomers(scope)) {
            outstanding = outstanding.add(billingService.currentOutstanding(customerId));
        }
        BigDecimal unpaid = FinanceTexts.ZERO;
        boolean hiddenCompanyPayable = false;
        for (Payable payable : finance.listPayables()) {
            if (payableVisible(scope, payable)) {
                unpaid = unpaid.add(payable.amount().subtract(payable.paidAmount()));
            } else if (payable.customerId() == null) {
                hiddenCompanyPayable = true;
            }
        }
        String limitation = "";
        if (!"COMPANY".equals(scope.kind()) && hiddenCompanyPayable) {
            limitation = "公司工资、采购或未归集外协不计入该范围应付";
        }
        if (!"COMPANY".equals(scope.kind()) && scope.customerIds().isEmpty()) {
            limitation = "授权范围内没有可归集客户";
        }
        return new CurrentBalances(asOf, FinanceTexts.money(outstanding), FinanceTexts.money(unpaid), limitation);
    }

    private String balanceReason(CurrentBalances balances) {
        String base = "截至本次查询的当前余额，不随所选月份改变";
        if (balances.limitation() == null || balances.limitation().isBlank()) {
            return base;
        }
        return base + "。" + balances.limitation();
    }

    private ReceivableRanking receivableRanking(FinanceDashboardScope scope) {
        List<ReceivableRankRow> rows = new ArrayList<>();
        for (Long customerId : balanceCustomers(scope)) {
            BigDecimal outstanding = billingService.currentOutstanding(customerId);
            if (outstanding.compareTo(FinanceTexts.ZERO) == 0) {
                continue;
            }
            rows.add(new ReceivableRankRow(customerId, customerName(customerId), outstanding));
        }
        rows.sort((left, right) -> {
            int amount = right.outstandingAmount().compareTo(left.outstandingAmount());
            if (amount != 0) {
                return amount;
            }
            return Long.compare(left.customerId(), right.customerId());
        });
        int total = rows.size();
        List<ReceivableRankRow> returned = rows.size() > RANK_LIMIT ? rows.subList(0, RANK_LIMIT) : rows;
        return new ReceivableRanking(total, returned.size(), total > returned.size(), returned);
    }

    private List<Long> balanceCustomers(FinanceDashboardScope scope) {
        if (!"COMPANY".equals(scope.kind())) {
            return scope.customerIds();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (ReceivableLine line : finance.listReceivables()) {
            ids.add(line.customerId());
        }
        for (CustomerReceipt receipt : finance.listReceipts()) {
            ids.add(receipt.customerId());
        }
        return List.copyOf(ids);
    }

    private boolean payableVisible(FinanceDashboardScope scope, Payable payable) {
        if ("COMPANY".equals(scope.kind())) {
            return true;
        }
        return payable.customerId() != null && scope.customerIds().contains(payable.customerId());
    }

    private List<FinanceDetailRow> revenueRows(FinanceDashboardScope scope, String yearMonth) {
        List<FinanceDetailRow> rows = new ArrayList<>();
        for (ReceivableLine line : finance.listReceivables()) {
            if (!yearMonth.equals(line.yearMonth()) || !customerInScope(scope, line.customerId())) {
                continue;
            }
            rows.add(detail("RECEIVABLE", line.code(), line.customerId(), line.customerName(), line.yearMonth(), line.amount(),
                    line.reversesCode().isBlank() ? "加工费账单" : "冲销 " + line.reversesCode()));
        }
        return rows;
    }

    private List<FinanceDetailRow> costRows(FinanceDashboardScope scope, String yearMonth) {
        List<FinanceDetailRow> rows = new ArrayList<>();
        boolean company = "COMPANY".equals(scope.kind());
        PayrollRun run = finance.findPayrollRun(yearMonth).orElse(null);
        boolean legacyConfirmed = run != null && run.confirmed();
        for (PayrollLine line : finance.listPayrollLines()) {
            if (!yearMonth.equals(line.yearMonth()) || !line.confirmed() || !customerInScope(scope, line.customerId())) {
                continue;
            }
            if (!PayrollLine.POST_DELIVERY.equals(line.sourceMode()) && !legacyConfirmed) {
                continue;
            }
            rows.add(detail("PAYROLL", line.code(), line.customerId(), customerName(line.customerId()), line.yearMonth(), line.netAmount(), line.processName()));
        }
        for (ConsumableMovement movement : finance.listConsumableMovements()) {
            if (!FinanceCostService.ISSUE.equals(movement.direction()) || !yearMonth.equals(movement.yearMonth())) {
                continue;
            }
            if (movement.customerId() == null) {
                if (!company) {
                    continue;
                }
            } else if (!customerInScope(scope, movement.customerId())) {
                continue;
            }
            rows.add(detail("CONSUMABLE", movement.code(), movement.customerId(),
                    movement.customerId() == null ? "" : customerName(movement.customerId()),
                    movement.yearMonth(), movement.amount(), "领用"));
        }
        if (company) {
            for (DepreciationEntry entry : finance.listDepreciations()) {
                if (yearMonth.equals(entry.yearMonth())) {
                    rows.add(detail("DEPRECIATION", entry.code(), null, "", entry.yearMonth(), entry.amount(), entry.assetCode()));
                }
            }
        }
        for (Payable payable : finance.listPayables()) {
            if (!"OUTSOURCE".equals(payable.kind()) || !yearMonth.equals(payable.yearMonth())) {
                continue;
            }
            if (payable.customerId() == null) {
                if (!company) {
                    continue;
                }
            } else if (!customerInScope(scope, payable.customerId())) {
                continue;
            }
            rows.add(detail("OUTSOURCE", payable.code(), payable.customerId(),
                    payable.customerId() == null ? "" : customerName(payable.customerId()),
                    payable.yearMonth(), payable.amount(), payable.payee()));
        }
        return rows;
    }

    private List<FinanceDetailRow> receivableRows(FinanceDashboardScope scope) {
        List<FinanceDetailRow> rows = new ArrayList<>();
        for (Long customerId : balanceCustomers(scope)) {
            for (ReceivableLine line : finance.listReceivables()) {
                if (line.customerId() != customerId) {
                    continue;
                }
                BigDecimal open = billingService.currentOpen(line);
                if (open.compareTo(FinanceTexts.ZERO) == 0) {
                    continue;
                }
                rows.add(detail("RECEIVABLE", line.code(), customerId, line.customerName(), line.yearMonth(), open, "当前未收"));
            }
        }
        return rows;
    }

    private List<FinanceDetailRow> payableRows(FinanceDashboardScope scope) {
        List<FinanceDetailRow> rows = new ArrayList<>();
        for (Payable payable : finance.listPayables()) {
            if (!payableVisible(scope, payable)) {
                continue;
            }
            BigDecimal unpaid = payable.amount().subtract(payable.paidAmount());
            if (unpaid.compareTo(FinanceTexts.ZERO) == 0) {
                continue;
            }
            rows.add(detail("PAYABLE", payable.code(), payable.customerId(),
                    payable.customerId() == null ? "" : customerName(payable.customerId()),
                    payable.yearMonth(), unpaid, payable.kind()));
        }
        return rows;
    }

    private boolean customerInScope(FinanceDashboardScope scope, long customerId) {
        if ("COMPANY".equals(scope.kind())) {
            return true;
        }
        return scope.customerIds().contains(customerId);
    }

    private FinanceDetailRow detail(
            String sourceType,
            String sourceCode,
            Long customerId,
            String customerName,
            String yearMonth,
            BigDecimal amount,
            String note
    ) {
        return new FinanceDetailRow(
                sourceType,
                sourceCode,
                customerId,
                customerName == null ? "" : customerName,
                yearMonth,
                FinanceTexts.money(amount),
                note == null ? "" : note
        );
    }

    private OperatingStatement aggregate(FinanceDashboardScope scope, String yearMonth) {
        if ("COMPANY".equals(scope.kind())) {
            return reportService.operating(yearMonth, null);
        }
        if (scope.customerIds().isEmpty()) {
            return new OperatingStatement(
                    yearMonth,
                    null,
                    FinanceTexts.ZERO,
                    FinanceTexts.ZERO,
                    FinanceTexts.ZERO,
                    FinanceTexts.ZERO,
                    FinanceTexts.ZERO,
                    FinanceTexts.ZERO,
                    List.of("授权范围内没有可归集客户")
            );
        }
        if (scope.customerIds().size() == 1) {
            return reportService.operating(yearMonth, scope.customerIds().get(0));
        }
        BigDecimal revenue = FinanceTexts.ZERO;
        BigDecimal wage = FinanceTexts.ZERO;
        BigDecimal consumable = FinanceTexts.ZERO;
        BigDecimal depreciation = FinanceTexts.ZERO;
        BigDecimal outsource = FinanceTexts.ZERO;
        List<String> gaps = new ArrayList<>();
        for (Long id : scope.customerIds()) {
            OperatingStatement one = reportService.operating(yearMonth, id);
            revenue = revenue.add(one.revenue());
            wage = wage.add(one.wageCost());
            consumable = consumable.add(one.consumableCost());
            depreciation = depreciation.add(one.depreciationCost());
            outsource = outsource.add(one.outsourceCost());
            for (String gap : one.gaps()) {
                if (!gaps.contains(gap)) {
                    gaps.add(gap);
                }
            }
        }
        BigDecimal result = revenue.subtract(wage).subtract(consumable).subtract(depreciation).subtract(outsource);
        return new OperatingStatement(yearMonth, null, revenue, wage, consumable, depreciation, outsource, result, gaps);
    }

    private List<Long> customerIdsInScope(OperatorContext context) {
        Set<Long> ids = new LinkedHashSet<>();
        for (PriceAgreement agreement : finance.listAgreements()) {
            if (readAuthorizer.customerVisible(context, agreement.customerId(), agreement.customerName())) {
                ids.add(agreement.customerId());
            }
        }
        for (ReceivableLine line : finance.listReceivables()) {
            if (readAuthorizer.customerVisible(context, line.customerId(), line.customerName())) {
                ids.add(line.customerId());
            }
        }
        return List.copyOf(ids);
    }

    private String customerName(long customerId) {
        for (PriceAgreement agreement : finance.listAgreements()) {
            if (agreement.customerId() == customerId && !agreement.customerName().isBlank()) {
                return agreement.customerName();
            }
        }
        for (ReceivableLine line : finance.listReceivables()) {
            if (line.customerId() == customerId && !line.customerName().isBlank()) {
                return line.customerName();
            }
        }
        return "";
    }
}
