package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.FinanceCapability;
import com.zhubao.erp.finance.domain.FinanceDashboard;
import com.zhubao.erp.finance.domain.FinanceDashboardScope;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FinanceTrendPoint;
import com.zhubao.erp.finance.domain.OperatingStatement;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceivableLine;
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

    private final FinanceReportService reportService;
    private final FinanceReadAuthorizer readAuthorizer;
    private final FinanceRepository finance;
    private final PermissionGuard permissionGuard;

    public FinanceDashboardService(
            FinanceReportService reportService,
            FinanceReadAuthorizer readAuthorizer,
            FinanceRepository finance,
            PermissionGuard permissionGuard
    ) {
        this.reportService = reportService;
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
        return new FinanceDashboard(
                selected.toString(),
                FinanceTexts.now(),
                scope,
                operating,
                trend,
                null,
                null,
                List.of(
                        new FinanceCapability("operating", true, ""),
                        new FinanceCapability("trend", true, "含选择月在内的六个月。当前自然月只截至本次查询，不与完整月计算增幅"),
                        new FinanceCapability("currentBalances", false, "当前余额尚未提供，不能把 0 当成没有欠款"),
                        new FinanceCapability("receivableRanking", false, "客户未收排行尚未提供，不能用空列表表示已经查过")
                )
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
