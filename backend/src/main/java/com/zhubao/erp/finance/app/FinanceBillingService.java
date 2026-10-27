package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.BillingRunResult;
import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.CustomerStatement;
import com.zhubao.erp.finance.domain.FinanceCommands.BillingRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PriceLineCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.RecordReceiptCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReverseReceivableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SavePriceAgreementCommand;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.PriceLine;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户协议、加工费入账、红字和收款。只读母单与工序交付，不嵌进收货和报工事务。
 */
@Service
public class FinanceBillingService {

    static final String STOCK_NOTE = "实物为当前结存，不按月份回放";

    private final FinanceRepository finance;
    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final AtomicLong sequence = new AtomicLong();

    public FinanceBillingService(
            FinanceRepository finance,
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService
    ) {
        this.finance = finance;
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
    }

    public List<PriceAgreement> agreements(Long customerId) {
        return finance.listAgreements().stream()
                .filter(agreement -> customerId == null || agreement.customerId() == customerId)
                .toList();
    }

    public PriceAgreement saveAgreement(SavePriceAgreementCommand command, String operator) {
        throw new BusinessException("客户协议已停止维护，历史协议只可查询。加工费请在交货后结单");
    }

    public BillingRunResult generate(BillingRunCommand command) {
        throw new BusinessException("已停止按收货或交工生成加工费，请在交货后结单");
    }

    public List<UnpricedItem> unpriced(String yearMonth, Long customerId) {
        return List.of();
    }

    private BillingRunResult run(BillingRunCommand command, boolean write) {
        YearMonth month = FinanceTexts.month(command.yearMonth());
        List<ReceivableLine> created = new ArrayList<>();
        List<UnpricedItem> unpriced = new ArrayList<>();
        for (MotherOrderRecord mother : documents.listMotherOrders()) {
            if (!sameMonth(motherMonth(mother), month) || skipped(command.customerId(), mother.customerId())) {
                continue;
            }
            billMother(mother, month, created, unpriced, write);
        }
        for (ProcessStationHandoff handoff : documents.listAllProcessStationHandoffs()) {
            if (!sameMonth(FinanceTexts.monthOfTimestamp(handoff.createdAt()), month)) {
                continue;
            }
            if (handoff.orderCode().startsWith("FG-")) {
                continue;
            }
            billProcess(handoff, month, command.customerId(), created, unpriced, write);
        }
        return new BillingRunResult(month.toString(), created.size(), created, unpriced);
    }

    /** 改单减额已停止。客户减额改由交货后结单调整处理。 */
    public ReceivableLine reverseForChange(
            String receivableCode,
            BigDecimal amount,
            String changeId,
            String operator,
            com.zhubao.erp.work.domain.WorkSession session,
            long customerId,
            String motherOrderCode,
            String workOrderCode
    ) {
        throw new BusinessException("改单不再冲减加工费，费用在交货后结单");
    }

    public ReceivableLine reverse(ReverseReceivableCommand command, String operator) {
        return reverse(command, operator, null);
    }

    private ReceivableLine reverse(ReverseReceivableCommand command, String operator, com.zhubao.erp.work.domain.WorkSession session) {
        String code = FinanceTexts.code("FIN-RED", sequence);
        ReceivableLine original = finance.findReceivableByCode(command.receivableCode())
                .orElseThrow(() -> new BusinessException("找不到要冲销的账单行"));
        ReceivableLine red = buildRed(command, operator, "RED|" + original.code() + "|" + code);
        if (session == null) {
            finance.saveReceivable(red);
        } else {
            finance.saveReceivable(red, session);
        }
        return red;
    }

    private ReceivableLine buildRed(ReverseReceivableCommand command, String operator, String sourceKey) {
        ReceivableLine original = finance.findReceivableByCode(command.receivableCode())
                .orElseThrow(() -> new BusinessException("找不到要冲销的账单行"));
        if (!original.reversesCode().isBlank() || "RED".equals(original.sourceType())) {
            throw new BusinessException("红字不能再冲销");
        }
        BigDecimal open = openAmount(original);
        BigDecimal amount = FinanceTexts.money(command.amount());
        if (amount.compareTo(open) > 0) {
            throw new BusinessException("冲销金额不能超过尚未核销的余额");
        }
        BigDecimal quantity = original.unitPrice().signum() == 0
                ? FinanceTexts.quantity(amount)
                : FinanceTexts.quantity(amount.divide(original.unitPrice(), 3, java.math.RoundingMode.HALF_UP));
        String code = sourceKey.startsWith("CHANGE-RED|") ? FinanceTexts.code("FIN-RED", sequence) : sourceKey.substring(sourceKey.lastIndexOf('|') + 1);
        ReceivableLine red = new ReceivableLine(
                code,
                original.customerId(),
                original.customerName(),
                YearMonth.now().toString(),
                "RED",
                sourceKey,
                original.motherOrderCode(),
                original.workOrderCode(),
                original.processName(),
                quantity,
                original.unit(),
                original.unitPrice(),
                amount.negate(),
                original.code(),
                command.reason().trim(),
                operator,
                FinanceTexts.now()
        );
        return red;
    }

    public CustomerReceipt receive(RecordReceiptCommand command, String operator) {
        LocalMonth received = receivedMonth(command.receivedOn());
        BigDecimal amount = FinanceTexts.money(command.amount());
        List<ReceiptAllocation> allocations = normalizeAllocations(command, amount);
        String idempotencyKey = command.idempotencyKey() == null ? "" : command.idempotencyKey().trim();
        if (idempotencyKey.length() > 80) {
            throw new BusinessException("收款幂等键不能超过 80 个字符");
        }
        CustomerReceipt receipt = new CustomerReceipt(
                FinanceTexts.code("FIN-RC", sequence),
                command.customerId(),
                command.customerName().trim(),
                received.month().toString(),
                amount,
                received.day().toString(),
                allocations,
                operator,
                FinanceTexts.now()
        );
        return finance.registerReceipt(receipt, idempotencyKey);
    }

    public CustomerStatement statement(long customerId, String yearMonth) {
        String month = FinanceTexts.blank(yearMonth);
        if (!month.isEmpty()) {
            FinanceTexts.month(month);
        }
        List<ReceivableLine> lines = finance.listReceivables().stream()
                .filter(line -> line.customerId() == customerId)
                .filter(line -> month.isEmpty() || month.equals(line.yearMonth()))
                .toList();
        List<CustomerReceipt> receipts = finance.listReceipts().stream()
                .filter(receipt -> receipt.customerId() == customerId)
                .filter(receipt -> month.isEmpty() || month.equals(receipt.yearMonth()))
                .toList();
        BigDecimal receivable = FinanceTexts.ZERO;
        for (ReceivableLine line : lines) {
            receivable = receivable.add(line.amount());
        }
        BigDecimal received = allocatedTo(lines);
        BigDecimal prepayment = FinanceTexts.ZERO;
        for (CustomerReceipt receipt : receipts) {
            BigDecimal used = FinanceTexts.ZERO;
            for (ReceiptAllocation allocation : receipt.allocations()) {
                used = used.add(allocation.amount());
            }
            prepayment = prepayment.add(receipt.amount().subtract(used));
        }
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (StockBalance balance : stockLedgerService.balances()) {
            Long owner = balance.dimension().customerId();
            if (owner != null && owner == customerId) {
                quantity = quantity.add(balance.quantity());
                weight = weight.add(balance.weight());
            }
        }
        return new CustomerStatement(
                customerId,
                customerName(customerId, lines, receipts),
                month,
                FinanceTexts.quantity(quantity),
                FinanceTexts.quantity(weight),
                FinanceTexts.money(receivable),
                FinanceTexts.money(received),
                FinanceTexts.money(receivable.subtract(received)),
                FinanceTexts.money(prepayment),
                lines,
                receipts,
                STOCK_NOTE
        );
    }

    private void billMother(
            MotherOrderRecord mother,
            YearMonth month,
            List<ReceivableLine> created,
            List<UnpricedItem> unpriced,
            boolean write
    ) {
        if (mother.customerId() == null) {
            unpriced.add(unpriced(month, null, mother.customerName(), mother.code(), "", "", "母单没有客户"));
            return;
        }
        Optional<PriceAgreement> agreement = activeAgreement(mother.customerId(), month);
        if (agreement.isEmpty()) {
            unpriced.add(unpriced(month, mother.customerId(), mother.customerName(), mother.code(), "", "", "没有生效的客户协议"));
            return;
        }
        if (agreement.get().mode() == PricingMode.PROCESS) {
            return;
        }
        String sourceKey = "INBOUND|" + mother.code();
        if (finance.findReceivableBySourceKey(sourceKey).isPresent()) {
            return;
        }
        PriceLine price = agreement.get().lines().get(0);
        BigDecimal quantity = price.unit() == ChargeUnit.GRAM ? mother.weight() : mother.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            unpriced.add(unpriced(month, mother.customerId(), mother.customerName(), mother.code(), "", "", "来料数量或重量无效"));
            return;
        }
        if (write) {
            created.add(post(
                    mother.customerId(),
                    mother.customerName(),
                    month,
                    "INBOUND",
                    sourceKey,
                    mother.code(),
                    "",
                    "",
                    quantity,
                    price,
                    ""
            ));
        }
    }

    private void billProcess(
            ProcessStationHandoff handoff,
            YearMonth month,
            Long customerFilter,
            List<ReceivableLine> created,
            List<UnpricedItem> unpriced,
            boolean write
    ) {
        Optional<BillableOrder> order = resolveOrder(handoff.orderCode());
        if (order.isEmpty()) {
            unpriced.add(unpriced(month, null, "", "", handoff.orderCode(), handoff.processName(), "找不到来源单据"));
            return;
        }
        BillableOrder billable = order.get();
        if (skipped(customerFilter, billable.customerId())) {
            return;
        }
        if (billable.customerId() == null) {
            unpriced.add(unpriced(month, null, billable.customerName(), billable.motherOrderCode(), billable.orderCode(), handoff.processName(), "单据没有客户"));
            return;
        }
        Optional<PriceAgreement> agreement = activeAgreement(billable.customerId(), month);
        if (agreement.isEmpty()) {
            unpriced.add(unpriced(
                    month,
                    billable.customerId(),
                    billable.customerName(),
                    billable.motherOrderCode(),
                    billable.orderCode(),
                    handoff.processName(),
                    "没有生效的客户协议"
            ));
            return;
        }
        if (agreement.get().mode() != PricingMode.PROCESS) {
            return;
        }
        Optional<PriceLine> price = agreement.get().lines().stream()
                .filter(line -> handoff.processName().equals(line.processName()))
                .findFirst();
        if (price.isEmpty()) {
            unpriced.add(unpriced(
                    month,
                    billable.customerId(),
                    billable.customerName(),
                    billable.motherOrderCode(),
                    billable.orderCode(),
                    handoff.processName(),
                    "协议没有这道工序的单价"
            ));
            return;
        }
        String sourceKey = "PROCESS|" + billable.orderCode() + "|" + handoff.processName();
        if (finance.findReceivableBySourceKey(sourceKey).isPresent()) {
            return;
        }
        BigDecimal quantity = switch (price.get().unit()) {
            case JOB -> BigDecimal.ONE;
            case GRAM -> billable.weight();
            case PIECE -> billable.quantity();
        };
        if (quantity == null || quantity.signum() <= 0) {
            unpriced.add(unpriced(
                    month,
                    billable.customerId(),
                    billable.customerName(),
                    billable.motherOrderCode(),
                    billable.orderCode(),
                    handoff.processName(),
                    "报工数量或重量无效"
            ));
            return;
        }
        if (write) {
            created.add(post(
                    billable.customerId(),
                    billable.customerName(),
                    month,
                    "PROCESS",
                    sourceKey,
                    billable.motherOrderCode(),
                    billable.orderCode(),
                    handoff.processName(),
                    quantity,
                    price.get(),
                    ""
            ));
        }
    }

    private ReceivableLine post(
            long customerId,
            String customerName,
            YearMonth month,
            String sourceType,
            String sourceKey,
            String motherOrderCode,
            String workOrderCode,
            String processName,
            BigDecimal quantity,
            PriceLine price,
            String remark
    ) {
        BigDecimal scaledQuantity = FinanceTexts.quantity(quantity);
        BigDecimal amount = FinanceTexts.money(scaledQuantity.multiply(price.unitPrice()));
        ReceivableLine line = new ReceivableLine(
                FinanceTexts.code("FIN-AR", sequence),
                customerId,
                FinanceTexts.blank(customerName),
                month.toString(),
                sourceType,
                sourceKey,
                motherOrderCode,
                workOrderCode,
                processName,
                scaledQuantity,
                price.unit(),
                FinanceTexts.price(price.unitPrice()),
                amount,
                "",
                remark,
                "财务入账",
                FinanceTexts.now()
        );
        finance.saveReceivable(line);
        return line;
    }

    private List<PriceLine> normalizeLines(PricingMode mode, List<PriceLineCommand> commands) {
        List<PriceLine> lines = new ArrayList<>();
        Set<String> processes = new LinkedHashSet<>();
        for (PriceLineCommand command : commands) {
            ChargeUnit unit = FinanceTexts.chargeUnit(command.unit());
            String process = FinanceTexts.blank(command.processName());
            if (mode == PricingMode.PROCESS) {
                FinanceTexts.requireProcess(process);
                if (!processes.add(process)) {
                    throw new BusinessException("同一道工序不能有两条单价");
                }
            } else if (!process.isEmpty()) {
                throw new BusinessException("按克或按件的协议不需要填写工序");
            }
            if (mode == PricingMode.WEIGHT && unit != ChargeUnit.GRAM) {
                throw new BusinessException("按克协议的单位必须是克");
            }
            if (mode == PricingMode.PIECE && unit != ChargeUnit.PIECE) {
                throw new BusinessException("按件协议的单位必须是件");
            }
            lines.add(new PriceLine(process, unit, FinanceTexts.price(command.unitPrice())));
        }
        if (mode != PricingMode.PROCESS && lines.size() != 1) {
            throw new BusinessException("按克或按件的协议只能有一条单价");
        }
        return lines;
    }

    private void closeOverlappingAgreements(long customerId, YearMonth from, YearMonth to) {
        for (PriceAgreement current : finance.listAgreements()) {
            if (current.customerId() != customerId) {
                continue;
            }
            if (!FinanceTexts.overlaps(current.effectiveFrom(), current.effectiveTo(), from, to)) {
                continue;
            }
            YearMonth currentFrom = YearMonth.parse(current.effectiveFrom());
            if (current.effectiveTo().isBlank() && currentFrom.isBefore(from)) {
                finance.saveAgreement(current.closeAt(from.minusMonths(1).toString()));
                continue;
            }
            throw new BusinessException("客户在这个月份已经有协议");
        }
    }

    private Optional<PriceAgreement> activeAgreement(long customerId, YearMonth month) {
        return finance.listAgreements().stream()
                .filter(agreement -> agreement.customerId() == customerId)
                .filter(agreement -> FinanceTexts.covers(agreement.effectiveFrom(), agreement.effectiveTo(), month))
                .findFirst();
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

    private List<ReceiptAllocation> normalizeAllocations(RecordReceiptCommand command, BigDecimal amount) {
        List<ReceiptAllocationCommand> requested = command.allocations() == null ? List.of() : command.allocations();
        BigDecimal used = FinanceTexts.ZERO;
        List<ReceiptAllocation> allocations = new ArrayList<>();
        for (ReceiptAllocationCommand requestedAllocation : requested) {
            ReceivableLine line = finance.findReceivableByCode(requestedAllocation.receivableCode())
                    .orElseThrow(() -> new BusinessException("找不到要核销的账单行"));
            if (line.customerId() != command.customerId()) {
                throw new BusinessException("不能核销其他客户的账单");
            }
            if (!line.reversesCode().isBlank()) {
                throw new BusinessException("红字不能直接核销");
            }
            BigDecimal allocation = FinanceTexts.money(requestedAllocation.amount());
            BigDecimal already = FinanceTexts.ZERO;
            for (ReceiptAllocation chosen : allocations) {
                if (line.code().equals(chosen.receivableCode())) {
                    already = already.add(chosen.amount());
                }
            }
            if (allocation.add(already).compareTo(openAmount(line)) > 0) {
                throw new BusinessException("核销金额不能超过尚未核销的余额");
            }
            used = used.add(allocation);
            allocations.add(new ReceiptAllocation(line.code(), allocation));
        }
        if (used.compareTo(amount) > 0) {
            throw new BusinessException("核销合计不能超过收款金额");
        }
        return allocations;
    }

    private BigDecimal openAmount(ReceivableLine line) {
        BigDecimal open = line.amount();
        for (ReceivableLine other : finance.listReceivables()) {
            if (line.code().equals(other.reversesCode())) {
                open = open.add(other.amount());
            }
        }
        for (CustomerReceipt receipt : finance.listReceipts()) {
            for (ReceiptAllocation allocation : receipt.allocations()) {
                if (line.code().equals(allocation.receivableCode())) {
                    open = open.subtract(allocation.amount());
                }
            }
        }
        return FinanceTexts.money(open);
    }

    /** 一条原始账单扣掉红字和已核销后的当前未收。红字行不单独再算一次。 */
    public BigDecimal currentOpen(ReceivableLine line) {
        if (line == null || !line.reversesCode().isBlank()) {
            return FinanceTexts.ZERO;
        }
        return openAmount(line);
    }

    /** 该客户全部账单、红字和核销后的当前未收。未分配预收不抵减这笔未收。 */
    public BigDecimal currentOutstanding(long customerId) {
        return statement(customerId, "").outstandingAmount();
    }

    private BigDecimal allocatedTo(List<ReceivableLine> lines) {
        Set<String> codes = new LinkedHashSet<>();
        for (ReceivableLine line : lines) {
            codes.add(line.code());
        }
        BigDecimal received = FinanceTexts.ZERO;
        for (CustomerReceipt receipt : finance.listReceipts()) {
            for (ReceiptAllocation allocation : receipt.allocations()) {
                if (codes.contains(allocation.receivableCode())) {
                    received = received.add(allocation.amount());
                }
            }
        }
        return received;
    }

    private String customerName(long customerId, List<ReceivableLine> lines, List<CustomerReceipt> receipts) {
        for (ReceivableLine line : lines) {
            if (!line.customerName().isBlank()) {
                return line.customerName();
            }
        }
        for (CustomerReceipt receipt : receipts) {
            if (!receipt.customerName().isBlank()) {
                return receipt.customerName();
            }
        }
        return finance.listAgreements().stream()
                .filter(agreement -> agreement.customerId() == customerId && !agreement.customerName().isBlank())
                .map(PriceAgreement::customerName)
                .findFirst()
                .orElse("客户 " + customerId);
    }

    private String motherMonth(MotherOrderRecord mother) {
        String inbound = FinanceTexts.monthOfTimestamp(mother.inboundDate());
        if (!inbound.isEmpty()) {
            return inbound;
        }
        return FinanceTexts.monthOfTimestamp(mother.createdAt());
    }

    private boolean sameMonth(String value, YearMonth month) {
        return month.toString().equals(value);
    }

    private boolean skipped(Long filter, Long customerId) {
        return filter != null && (customerId == null || filter.longValue() != customerId);
    }

    private UnpricedItem unpriced(
            YearMonth month,
            Long customerId,
            String customerName,
            String motherOrderCode,
            String workOrderCode,
            String processName,
            String reason
    ) {
        return new UnpricedItem(month.toString(), customerId, customerName, motherOrderCode, workOrderCode, processName, reason);
    }

    private LocalMonth receivedMonth(String receivedOn) {
        var day = FinanceTexts.day(receivedOn);
        return new LocalMonth(day, YearMonth.from(day));
    }

    private record LocalMonth(java.time.LocalDate day, YearMonth month) {
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
