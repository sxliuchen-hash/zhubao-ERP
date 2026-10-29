package com.zhubao.erp.finance.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.BillingRunResult;
import com.zhubao.erp.finance.domain.CustomerReceipt;
import com.zhubao.erp.finance.domain.CustomerStatement;
import com.zhubao.erp.finance.domain.FinanceCommands.BillingRunCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReceiptAllocationCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.RecordReceiptCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.ReverseReceivableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SavePriceAgreementCommand;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.ReceiptAllocation;
import com.zhubao.erp.finance.domain.ReceiptAllocationOperation;
import com.zhubao.erp.finance.domain.ReceivableLine;
import com.zhubao.erp.finance.domain.UnpricedItem;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 历史协议、客户账、红字和收款。加工费及工资由交货后结单统一入账。
 */
@Service
public class FinanceBillingService {

    static final String STOCK_NOTE = "实物为当前结存，不按月份回放";

    private final FinanceRepository finance;
    private final StockLedgerService stockLedgerService;
    private final PendingSettlementQuery pendingSettlements;
    private final AtomicLong sequence = new AtomicLong();

    public FinanceBillingService(FinanceRepository finance, StockLedgerService stocks) {
        this(finance, stocks, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FinanceBillingService(FinanceRepository finance, StockLedgerService stocks, PendingSettlementQuery pendingSettlements) {
        this.finance = finance;
        this.stockLedgerService = stocks;
        this.pendingSettlements = pendingSettlements;
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
        FinanceTexts.month(yearMonth);
        return pendingSettlements == null ? List.of() : pendingSettlements.list(yearMonth, customerId);
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
        String idempotencyKey = command.idempotencyKey() == null ? "" : command.idempotencyKey().trim();
        if (idempotencyKey.length() > 80) {
            throw new BusinessException("收款幂等键不能超过 80 个字符");
        }
        if (!idempotencyKey.isEmpty()) {
            Optional<CustomerReceipt> stored = finance.findReceiptByIdempotency(idempotencyKey);
            if (stored.isPresent()) {
                if (!sameReceiptRequest(stored.get(), command, amount, received.day().toString())) {
                    throw new BusinessException("同一幂等键的收款内容不一致");
                }
                return stored.get();
            }
        }
        List<ReceiptAllocation> allocations = normalizeAllocations(command, amount);
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
            prepayment = prepayment.add(receipt.amount().subtract(usedOnReceipt(receipt)));
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

    /** 回放只核对原请求内容，不再用已经下降的未收余额拒绝同一笔成功收款。 */
    private boolean sameReceiptRequest(CustomerReceipt existing, RecordReceiptCommand command, BigDecimal amount, String receivedOn) {
        if (existing.customerId() != command.customerId()
                || existing.amount().compareTo(amount) != 0
                || !existing.receivedOn().equals(receivedOn)
                || !existing.customerName().equals(command.customerName().trim())) {
            return false;
        }
        List<ReceiptAllocationCommand> requested = command.allocations() == null ? List.of() : command.allocations();
        if (existing.allocations().size() != requested.size()) {
            return false;
        }
        List<ReceiptAllocation> left = existing.allocations().stream()
                .sorted(Comparator.comparing(ReceiptAllocation::receivableCode).thenComparing(ReceiptAllocation::amount))
                .toList();
        List<ReceiptAllocationCommand> right = requested.stream()
                .sorted(Comparator.comparing(ReceiptAllocationCommand::receivableCode).thenComparing(ReceiptAllocationCommand::amount))
                .toList();
        for (int index = 0; index < left.size(); index++) {
            if (!left.get(index).receivableCode().equals(right.get(index).receivableCode())
                    || left.get(index).amount().compareTo(FinanceTexts.money(right.get(index).amount())) != 0) {
                return false;
            }
        }
        return true;
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
        for (ReceiptAllocationOperation operation : finance.listAllocationOperations()) {
            for (ReceiptAllocation allocation : operation.lines()) {
                if (!line.code().equals(allocation.receivableCode())) {
                    continue;
                }
                if (ReceiptAllocationOperation.REVOKE.equals(operation.action())) {
                    open = open.add(allocation.amount());
                } else {
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
        for (ReceiptAllocationOperation operation : finance.listAllocationOperations()) {
            for (ReceiptAllocation allocation : operation.lines()) {
                if (!codes.contains(allocation.receivableCode())) {
                    continue;
                }
                received = ReceiptAllocationOperation.REVOKE.equals(operation.action())
                        ? received.subtract(allocation.amount())
                        : received.add(allocation.amount());
            }
        }
        return received;
    }

    private BigDecimal usedOnReceipt(CustomerReceipt receipt) {
        BigDecimal used = FinanceTexts.ZERO;
        for (ReceiptAllocation allocation : receipt.allocations()) {
            used = used.add(allocation.amount());
        }
        for (ReceiptAllocationOperation operation : finance.listAllocationOperations()) {
            if (!receipt.code().equals(operation.receiptCode())) {
                continue;
            }
            for (ReceiptAllocation allocation : operation.lines()) {
                used = ReceiptAllocationOperation.REVOKE.equals(operation.action()) ? used.subtract(allocation.amount()) : used.add(allocation.amount());
            }
        }
        return used;
    }

    public ReceiptAllocationOperation allocateExisting(String receiptCode, String requestId, String action, String reason, List<ReceiptAllocationCommand> requested, String operator) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{8,80}")) {
            throw new BusinessException("核销请求号无效");
        }
        CustomerReceipt receipt = finance.listReceipts().stream()
                .filter(row -> row.code().equals(receiptCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到要核销的收款"));
        List<ReceiptAllocation> lines = new ArrayList<>();
        for (ReceiptAllocationCommand item : requested == null ? List.<ReceiptAllocationCommand>of() : requested) {
            lines.add(new ReceiptAllocation(item.receivableCode(), FinanceTexts.money(item.amount())));
        }
        return finance.registerAllocationOperation(new ReceiptAllocationOperation(
                FinanceTexts.code("FIN-AO", sequence), receipt.code(), receipt.customerId(), action, reason,
                operator, requestId, FinanceTexts.now(), lines
        ));
    }

    public CustomerReceipt receipt(String code) {
        return finance.listReceipts().stream()
                .filter(row -> row.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到要核销的收款"));
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

    private LocalMonth receivedMonth(String receivedOn) {
        var day = FinanceTexts.day(receivedOn);
        return new LocalMonth(day, YearMonth.from(day));
    }

    private record LocalMonth(java.time.LocalDate day, YearMonth month) {
    }

}
