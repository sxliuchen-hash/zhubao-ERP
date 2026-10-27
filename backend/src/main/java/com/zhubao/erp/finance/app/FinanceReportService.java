package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.ConsumableMovement;
import com.zhubao.erp.finance.domain.DepreciationEntry;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.FixedAsset;
import com.zhubao.erp.finance.domain.OperatingStatement;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayableView;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.ReceivableLine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 应付汇总和月度经营。折旧只出现在公司整体，不摊到单个客户。
 */
@Service
public class FinanceReportService {

    private final FinanceRepository finance;
    private final FinanceBillingService billingService;

    public FinanceReportService(FinanceRepository finance, FinanceBillingService billingService) {
        this.finance = finance;
        this.billingService = billingService;
    }

    public List<PayableView> payables(String yearMonth) {
        String month = FinanceTexts.blank(yearMonth);
        if (!month.isEmpty()) {
            FinanceTexts.month(month);
        }
        return finance.listPayables().stream()
                .filter(payable -> month.isEmpty() || month.equals(payable.yearMonth()))
                .map(this::view)
                .toList();
    }

    public OperatingStatement operating(String yearMonth, Long customerId) {
        YearMonth month = FinanceTexts.month(yearMonth);
        List<String> gaps = new ArrayList<>();
        BigDecimal revenue = FinanceTexts.ZERO;
        for (ReceivableLine line : finance.listReceivables()) {
            if (month.toString().equals(line.yearMonth()) && matchesCustomer(customerId, line.customerId())) {
                revenue = revenue.add(line.amount());
            }
        }
        BigDecimal wage = FinanceTexts.ZERO;
        boolean legacyMonthOpen = true;
        PayrollRun run = finance.findPayrollRun(month.toString()).orElse(null);
        if (run != null && run.confirmed()) {
            legacyMonthOpen = false;
        }
        boolean legacyWaiting = false;
        for (PayrollLine line : finance.listPayrollLines()) {
            if (!month.toString().equals(line.yearMonth()) || !matchesCustomer(customerId, line.customerId())) {
                continue;
            }
            boolean postDelivery = PayrollLine.POST_DELIVERY.equals(line.sourceMode());
            if (postDelivery) {
                if (line.confirmed()) {
                    wage = wage.add(line.netAmount());
                }
                continue;
            }
            if (!line.confirmed() || legacyMonthOpen) {
                legacyWaiting = true;
                continue;
            }
            wage = wage.add(line.netAmount());
        }
        if (legacyWaiting) {
            gaps.add("历史月度工资未确认，这部分成本按 0");
        }
        BigDecimal consumable = FinanceTexts.ZERO;
        for (ConsumableMovement movement : finance.listConsumableMovements()) {
            if (!FinanceCostService.ISSUE.equals(movement.direction()) || !month.toString().equals(movement.yearMonth())) {
                continue;
            }
            if (customerId == null || customerId.equals(movement.customerId())) {
                consumable = consumable.add(movement.amount());
            }
        }
        BigDecimal depreciation = FinanceTexts.ZERO;
        if (customerId == null) {
            boolean due = false;
            for (FixedAsset asset : finance.listAssets()) {
                int index = FinanceTexts.depreciationIndex(asset.startMonth(), month.toString());
                if (index >= 1 && index <= asset.usefulMonths()) {
                    due = true;
                    break;
                }
            }
            List<DepreciationEntry> entries = finance.listDepreciations().stream()
                    .filter(entry -> month.toString().equals(entry.yearMonth()))
                    .toList();
            if (due && entries.isEmpty()) {
                gaps.add("折旧未生成，成本按 0");
            }
            for (DepreciationEntry entry : entries) {
                depreciation = depreciation.add(entry.amount());
            }
        }
        BigDecimal outsource = FinanceTexts.ZERO;
        for (Payable payable : finance.listPayables()) {
            if (!"OUTSOURCE".equals(payable.kind()) || !month.toString().equals(payable.yearMonth())) {
                continue;
            }
            if (customerId == null || customerId.equals(payable.customerId())) {
                outsource = outsource.add(payable.amount());
            }
        }
        if (!billingService.unpriced(month.toString(), customerId).isEmpty()) {
            gaps.add("已交货但尚未结单，收入未包含这些单");
        }
        BigDecimal result = revenue.subtract(wage).subtract(consumable).subtract(depreciation).subtract(outsource);
        return new OperatingStatement(
                month.toString(),
                customerId,
                FinanceTexts.money(revenue),
                FinanceTexts.money(wage),
                FinanceTexts.money(consumable),
                FinanceTexts.money(depreciation),
                FinanceTexts.money(outsource),
                FinanceTexts.money(result),
                gaps
        );
    }

    private PayableView view(Payable payable) {
        return new PayableView(
                payable.code(),
                payable.kind(),
                payable.yearMonth(),
                payable.payee(),
                payable.sourceCode(),
                payable.customerId(),
                payable.motherOrderCode(),
                payable.amount(),
                payable.paidAmount(),
                FinanceTexts.money(payable.amount().subtract(payable.paidAmount()))
        );
    }

    private boolean matchesCustomer(Long filter, long customerId) {
        return filter == null || filter == customerId;
    }
}
