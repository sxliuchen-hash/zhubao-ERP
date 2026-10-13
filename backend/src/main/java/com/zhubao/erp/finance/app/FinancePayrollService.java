package com.zhubao.erp.finance.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceCommands.PayrollRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveWorkerRateCommand;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.domain.PayrollGenerateResult;
import com.zhubao.erp.finance.domain.PayrollLine;
import com.zhubao.erp.finance.domain.PayrollRun;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.finance.domain.WorkerRate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工人单价和月度工资。确认后锁定，并形成应付，不生成银行代发。
 */
@Service
public class FinancePayrollService {

    private final FinanceRepository finance;
    private final BusinessDocumentRepository documents;
    private final ApprovalService approvalService;
    private final AtomicLong sequence = new AtomicLong();

    public FinancePayrollService(
            FinanceRepository finance,
            BusinessDocumentRepository documents,
            ApprovalService approvalService
    ) {
        this.finance = finance;
        this.documents = documents;
        this.approvalService = approvalService;
    }

    public List<WorkerRate> rates() {
        return finance.listWorkerRates();
    }

    public List<PayrollLine> lines(String yearMonth) {
        String month = FinanceTexts.blank(yearMonth);
        if (!month.isEmpty()) {
            FinanceTexts.month(month);
        }
        return finance.listPayrollLines().stream()
                .filter(line -> month.isEmpty() || month.equals(line.yearMonth()))
                .toList();
    }

    public WorkerRate saveRate(SaveWorkerRateCommand command, String operator) {
        FinanceTexts.requireProcess(command.processName());
        ChargeUnit unit = FinanceTexts.chargeUnit(command.unit());
        YearMonth from = FinanceTexts.month(command.effectiveFrom());
        YearMonth to = FinanceTexts.blank(command.effectiveTo()).isEmpty()
                ? null
                : FinanceTexts.month(command.effectiveTo());
        if (to != null && to.isBefore(from)) {
            throw new BusinessException("工价结束月份不能早于开始月份");
        }
        closeOverlappingRates(command.processName().trim(), from, to);
        WorkerRate rate = new WorkerRate(
                FinanceTexts.code("FIN-WR", sequence),
                command.processName().trim(),
                unit,
                FinanceTexts.price(command.unitPrice()),
                from.toString(),
                to == null ? "" : to.toString(),
                operator,
                FinanceTexts.now()
        );
        finance.saveWorkerRate(rate);
        return rate;
    }

    public PayrollGenerateResult generate(PayrollRunCommand command) {
        YearMonth month = FinanceTexts.month(command.yearMonth());
        ensureOpen(month);
        Built built = build(month);
        List<PayrollLine> merged = withAdjustments(month, built.lines());
        finance.replacePayroll(month.toString(), merged);
        return new PayrollGenerateResult(month.toString(), merged, built.missing());
    }

    public PayrollRun confirm(PayrollRunCommand command, String operator) {
        YearMonth month = FinanceTexts.month(command.yearMonth());
        ensureOpen(month);
        Built built = build(month);
        List<PayrollLine> merged = withAdjustments(month, built.lines());
        if (!built.missing().isEmpty()) {
            throw new BusinessException("仍有工序没有工人单价，不能确认工资");
        }
        if (merged.isEmpty()) {
            throw new BusinessException("本月没有可确认的报工");
        }
        List<PayrollLine> locked = merged.stream().map(PayrollLine::confirm).toList();
        finance.replacePayroll(month.toString(), locked);
        PayrollRun run = new PayrollRun(month.toString(), true, operator, FinanceTexts.now());
        finance.savePayrollRun(run);
        createPayables(month, locked, operator);
        return run;
    }

    private List<PayrollLine> withAdjustments(YearMonth month, List<PayrollLine> generated) {
        List<PayrollLine> adjustments = finance.listPayrollLines().stream()
                .filter(line -> month.toString().equals(line.yearMonth()))
                .filter(line -> line.sourceKey() != null && line.sourceKey().startsWith("PAY-ADJ|"))
                .toList();
        List<PayrollLine> merged = new ArrayList<>(generated);
        merged.addAll(adjustments);
        return merged;
    }

    private void ensureOpen(YearMonth month) {
        finance.findPayrollRun(month.toString())
                .filter(PayrollRun::confirmed)
                .ifPresent(run -> {
                    throw new BusinessException("该月工资已锁定");
                });
    }

    private Built build(YearMonth month) {
        List<PayrollLine> lines = new ArrayList<>();
        List<UnpricedItem> missing = new ArrayList<>();
        for (ProcessStationHandoff handoff : documents.listAllProcessStationHandoffs()) {
            if (!month.toString().equals(FinanceTexts.monthOfTimestamp(handoff.createdAt()))) {
                continue;
            }
            if (handoff.orderCode().startsWith("FG-")) {
                continue;
            }
            Optional<BillableOrder> order = resolveOrder(handoff.orderCode());
            if (order.isEmpty()) {
                missing.add(new UnpricedItem(
                        month.toString(),
                        null,
                        "",
                        "",
                        handoff.orderCode(),
                        handoff.processName(),
                        "找不到来源单据"
                ));
                continue;
            }
            Optional<WorkerRate> rate = activeRate(handoff.processName(), month);
            if (rate.isEmpty()) {
                missing.add(new UnpricedItem(
                        month.toString(),
                        order.get().customerId(),
                        order.get().customerName(),
                        order.get().motherOrderCode(),
                        order.get().orderCode(),
                        handoff.processName(),
                        "没有生效的工人单价"
                ));
                continue;
            }
            BillableOrder billable = order.get();
            ChargeUnit unit = rate.get().unit();
            BigDecimal quantity = switch (unit) {
                case JOB -> BigDecimal.ONE;
                case GRAM -> billable.weight();
                case PIECE -> billable.quantity();
            };
            if (quantity == null || quantity.signum() <= 0 || billable.customerId() == null) {
                missing.add(new UnpricedItem(
                        month.toString(),
                        billable.customerId(),
                        billable.customerName(),
                        billable.motherOrderCode(),
                        billable.orderCode(),
                        handoff.processName(),
                        "报工数量或客户无效"
                ));
                continue;
            }
            BigDecimal scaledQuantity = FinanceTexts.quantity(quantity);
            BigDecimal gross = FinanceTexts.money(scaledQuantity.multiply(rate.get().unitPrice()));
            BigDecimal deduction = lossDeduction(billable.orderCode(), handoff.processName(), gross);
            BigDecimal net = FinanceTexts.money(gross.subtract(deduction).max(BigDecimal.ZERO));
            lines.add(new PayrollLine(
                    FinanceTexts.code("FIN-PAY", sequence),
                    month.toString(),
                    FinanceTexts.blank(handoff.operator()),
                    billable.orderCode(),
                    handoff.processName(),
                    billable.motherOrderCode(),
                    billable.customerId(),
                    scaledQuantity,
                    unit,
                    FinanceTexts.price(rate.get().unitPrice()),
                    gross,
                    deduction,
                    net,
                    false,
                    "PAY|" + billable.orderCode() + "|" + handoff.processName()
            ));
        }
        lines.sort(Comparator.comparing(PayrollLine::orderCode).thenComparing(PayrollLine::processName));
        return new Built(lines, missing);
    }

    private void createPayables(YearMonth month, List<PayrollLine> lines, String operator) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PayrollLine line : lines) {
            totals.merge(line.operatorName(), line.netAmount(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            String source = "PAYROLL|" + month + "|" + entry.getKey();
            if (finance.findPayableBySource(source).isPresent()) {
                continue;
            }
            finance.savePayable(new Payable(
                    FinanceTexts.code("FIN-AP", sequence),
                    "PAYROLL",
                    month.toString(),
                    entry.getKey(),
                    source,
                    null,
                    "",
                    FinanceTexts.money(entry.getValue()),
                    FinanceTexts.ZERO,
                    operator,
                    FinanceTexts.now()
            ));
        }
    }

    private BigDecimal lossDeduction(String orderCode, String processName, BigDecimal gross) {
        BigDecimal deduction = FinanceTexts.ZERO;
        for (ApprovalTask task : approvalService.findAll()) {
            if (!"LOSS_APPROVAL".equals(task.scene()) || !orderCode.equals(task.businessCode())) {
                continue;
            }
            if (task.status() != ApprovalStatus.APPROVED && task.status() != ApprovalStatus.AUTO_APPROVED) {
                continue;
            }
            if (task.title() == null || !task.title().contains(processName)) {
                continue;
            }
            deduction = deduction.max(FinanceTexts.summaryAmount(task.sourceSummary(), "lossDeduction"));
        }
        return FinanceTexts.money(deduction.min(gross));
    }

    private Optional<WorkerRate> activeRate(String processName, YearMonth month) {
        return finance.listWorkerRates().stream()
                .filter(rate -> processName.equals(rate.processName()))
                .filter(rate -> FinanceTexts.covers(rate.effectiveFrom(), rate.effectiveTo(), month))
                .findFirst();
    }

    private void closeOverlappingRates(String processName, YearMonth from, YearMonth to) {
        for (WorkerRate current : finance.listWorkerRates()) {
            if (!processName.equals(current.processName())) {
                continue;
            }
            if (!FinanceTexts.overlaps(current.effectiveFrom(), current.effectiveTo(), from, to)) {
                continue;
            }
            YearMonth currentFrom = YearMonth.parse(current.effectiveFrom());
            if (current.effectiveTo().isBlank() && currentFrom.isBefore(from)) {
                finance.saveWorkerRate(current.closeAt(from.minusMonths(1).toString()));
                continue;
            }
            throw new BusinessException("这道工序在这个月份已经有工人单价");
        }
    }

    private Optional<BillableOrder> resolveOrder(String orderCode) {
        Optional<ChildWorkOrderRecord> child = documents.findChildWorkOrderByCode(orderCode);
        if (child.isPresent()) {
            ChildWorkOrderRecord record = child.get();
            return Optional.of(new BillableOrder(
                    record.code(),
                    record.motherOrderCode(),
                    record.customerId(),
                    record.customerName(),
                    record.quantity(),
                    record.weight()
            ));
        }
        return documents.findMotherOrderByCode(orderCode)
                .map(mother -> new BillableOrder(
                        mother.code(),
                        mother.code(),
                        mother.customerId(),
                        mother.customerName(),
                        mother.quantity(),
                        mother.weight()
                ));
    }

    private record Built(List<PayrollLine> lines, List<UnpricedItem> missing) {
    }

    private record BillableOrder(
            String orderCode,
            String motherOrderCode,
            Long customerId,
            String customerName,
            BigDecimal quantity,
            BigDecimal weight
    ) {
    }
}
