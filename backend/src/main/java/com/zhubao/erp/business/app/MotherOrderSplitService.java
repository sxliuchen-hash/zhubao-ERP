package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ChildWorkOrderView;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderSplitView;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.VerificationCodes;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 母单拆分：不拆仍用母单号；拆开则生成子单，并把对应数量从待加工转入在制。
 */
@Service
public class MotherOrderSplitService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final AtomicInteger childSequence = new AtomicInteger();

    public MotherOrderSplitService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
    }

    public MotherOrderSplitView view(String motherOrderCode) {
        return toView(requireMother(motherOrderCode));
    }

    /** 不拆：生成母单验证码，不移动库存。 */
    public MotherOrderSplitView keep(String motherOrderCode, String operator) {
        MotherOrderRecord mother = requireMother(motherOrderCode);
        if (!documents.listChildWorkOrders(mother.code()).isEmpty()) {
            throw new BusinessException("这张母单已经拆成子单，不能再选择不拆");
        }
        MotherSplitState current = documents.findMotherSplitState(mother.code()).orElse(null);
        if (current != null && MotherSplitState.SPLIT.equals(current.decision())) {
            throw new BusinessException("这张母单已经拆成子单，不能再选择不拆");
        }
        if (current != null && MotherSplitState.KEEP.equals(current.decision()) && !current.verificationCode().isBlank()) {
            return toView(mother);
        }
        String verificationCode = VerificationCodes.derive(mother.code(), documents::verificationCodeExists);
        documents.saveMotherSplitState(new MotherSplitState(
                mother.code(),
                MotherSplitState.KEEP,
                verificationCode,
                operatorOf(operator),
                RECORD_TIME.format(LocalDateTime.now())
        ));
        return toView(mother);
    }

    /** 拆成多张子单。数量、重量合计不得超过母单剩余，库存走台账从待加工转入在制。 */
    public MotherOrderSplitView split(SplitMotherOrderCommand command) {
        if (command == null) {
            throw new BusinessException("拆分命令不能为空");
        }
        MotherOrderRecord mother = requireMother(command.motherOrderCode());
        MotherSplitState current = documents.findMotherSplitState(mother.code()).orElse(null);
        if (current != null && MotherSplitState.KEEP.equals(current.decision())) {
            throw new BusinessException("这张母单已选择不拆，不能再拆子单");
        }
        List<SplitMotherOrderLine> lines = command.lines();
        if (lines.size() < 2) {
            throw new BusinessException("拆成多张至少需要两张子单");
        }
        List<ChildWorkOrderRecord> existing = documents.listChildWorkOrders(mother.code());
        BigDecimal remainingQuantity = remaining(mother.quantity(), existing, true);
        BigDecimal remainingWeight = remaining(mother.weight(), existing, false);
        BigDecimal requestedQuantity = ZERO;
        BigDecimal requestedWeight = ZERO;
        List<PreparedLine> prepared = new ArrayList<>();
        for (SplitMotherOrderLine line : lines) {
            if (line == null || line.quantity() == null || line.weight() == null
                    || line.quantity().compareTo(ZERO) <= 0 || line.weight().compareTo(ZERO) <= 0) {
                throw new BusinessException("拆出的数量和重量必须大于 0");
            }
            requestedQuantity = requestedQuantity.add(line.quantity());
            requestedWeight = requestedWeight.add(line.weight());
            prepared.add(new PreparedLine(
                    nextChildCode(),
                    WorkshopProcesses.normalize(line.processes()),
                    line.quantity(),
                    line.weight(),
                    ""
            ));
        }
        if (requestedQuantity.compareTo(remainingQuantity) > 0) {
            throw new BusinessException("拆出的数量合计不得超过母单剩余");
        }
        if (requestedWeight.compareTo(remainingWeight) > 0) {
            throw new BusinessException("拆出的重量合计不得超过母单剩余");
        }
        Set<String> reservedCodes = new HashSet<>();
        List<PreparedLine> withCodes = new ArrayList<>();
        for (PreparedLine line : prepared) {
            String verificationCode = VerificationCodes.derive(
                    line.code(),
                    candidate -> reservedCodes.contains(candidate) || documents.verificationCodeExists(candidate)
            );
            reservedCodes.add(verificationCode);
            withCodes.add(line.withVerification(verificationCode));
        }
        String operator = operatorOf(command.operator());
        String createdAt = RECORD_TIME.format(LocalDateTime.now());
        for (PreparedLine line : withCodes) {
            StockLedgerEntry entry = moveToWip(mother, line, operator);
            documents.saveChildWorkOrder(new ChildWorkOrderRecord(
                    line.code(),
                    mother.code(),
                    mother.customerId(),
                    blank(mother.customerName()),
                    blank(mother.processingRequirement()),
                    blank(mother.dueDate()),
                    blank(mother.materialCode()),
                    blank(mother.goodsName()),
                    line.quantity(),
                    blankToDefault(mother.quantityUnit(), "件"),
                    line.weight(),
                    blank(mother.warehouseCode()),
                    WorkshopProcesses.join(line.processes()),
                    line.verificationCode(),
                    entry.code(),
                    operator,
                    createdAt
            ));
        }
        documents.saveMotherSplitState(new MotherSplitState(
                mother.code(),
                MotherSplitState.SPLIT,
                "",
                operator,
                createdAt
        ));
        return toView(mother);
    }

    private StockLedgerEntry moveToWip(MotherOrderRecord mother, PreparedLine line, String operator) {
        OwnerType ownerType = ownerTypeOf(mother);
        Long ownerCustomerId = mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId();
        StockDimension fromWaiting = new StockDimension(
                ownerType,
                ownerCustomerId,
                mother.customerId(),
                mother.code(),
                null,
                mother.materialCode(),
                mother.warehouseCode(),
                StockStatus.RAW_WAITING
        );
        StockDimension toWip = new StockDimension(
                ownerType,
                ownerCustomerId,
                mother.customerId(),
                mother.code(),
                line.code(),
                mother.materialCode(),
                mother.warehouseCode(),
                StockStatus.WIP
        );
        return stockLedgerService.transfer(new StockTransferCommand(
                "work_order_split",
                line.code(),
                fromWaiting,
                toWip,
                line.quantity(),
                line.weight(),
                "拆分子单；工序：" + WorkshopProcesses.join(line.processes()) + "；操作人：" + operator
        ));
    }

    private MotherOrderSplitView toView(MotherOrderRecord mother) {
        List<ChildWorkOrderRecord> children = documents.listChildWorkOrders(mother.code());
        MotherSplitState state = documents.findMotherSplitState(mother.code()).orElse(null);
        String decision = state == null ? "" : state.decision();
        String verificationCode = state == null ? "" : blank(state.verificationCode());
        List<ChildWorkOrderView> childViews = children.stream().map(this::childView).toList();
        return new MotherOrderSplitView(
                mother.code(),
                decision,
                decisionLabel(decision),
                verificationCode,
                blank(mother.customerName()),
                blank(mother.processingRequirement()),
                blank(mother.dueDate()),
                blank(mother.goodsName()),
                blankToDefault(mother.quantityUnit(), "件"),
                mother.quantity(),
                mother.weight(),
                remaining(mother.quantity(), children, true),
                remaining(mother.weight(), children, false),
                "JG|mother|" + mother.code(),
                childViews
        );
    }

    private ChildWorkOrderView childView(ChildWorkOrderRecord record) {
        return new ChildWorkOrderView(
                record.code(),
                record.motherOrderCode(),
                record.verificationCode(),
                record.customerName(),
                record.processingRequirement(),
                record.dueDate(),
                record.goodsName(),
                record.quantity(),
                record.weight(),
                WorkshopProcesses.split(record.processes()),
                "JG|work_order|" + record.code()
        );
    }

    private BigDecimal remaining(BigDecimal total, List<ChildWorkOrderRecord> children, boolean quantity) {
        BigDecimal base = total == null ? ZERO : total;
        BigDecimal used = ZERO;
        for (ChildWorkOrderRecord child : children) {
            BigDecimal part = quantity ? child.quantity() : child.weight();
            if (part != null) {
                used = used.add(part);
            }
        }
        return base.subtract(used);
    }

    private String decisionLabel(String decision) {
        if (MotherSplitState.KEEP.equals(decision)) {
            return "不拆";
        }
        if (MotherSplitState.SPLIT.equals(decision)) {
            return "已拆成子单";
        }
        return "未选择";
    }

    private MotherOrderRecord requireMother(String motherOrderCode) {
        if (motherOrderCode == null || motherOrderCode.isBlank()) {
            throw new BusinessException("缺少母单号");
        }
        return documents.findMotherOrderByCode(motherOrderCode.trim())
                .orElseThrow(() -> new BusinessException("母单不存在：" + motherOrderCode));
    }

    private String nextChildCode() {
        String day = LocalDate.now().format(DAY);
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = "ZD-" + day + "-" + String.format("%03d", childSequence.incrementAndGet());
            if (!documents.childWorkOrderExists(code)) {
                return code;
            }
        }
        throw new BusinessException("子单号生成失败，请重试");
    }

    private OwnerType ownerTypeOf(MotherOrderRecord mother) {
        if (mother.ownerType() == null || mother.ownerType().isBlank()) {
            return OwnerType.CUSTOMER;
        }
        try {
            return OwnerType.valueOf(mother.ownerType().trim());
        } catch (IllegalArgumentException ex) {
            return OwnerType.CUSTOMER;
        }
    }

    private String operatorOf(String operator) {
        if (operator == null || operator.isBlank()) {
            return "现场";
        }
        return operator.trim();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String text = blank(value);
        return text.isEmpty() ? fallback : text;
    }

    private record PreparedLine(
            String code,
            List<String> processes,
            BigDecimal quantity,
            BigDecimal weight,
            String verificationCode
    ) {
        private PreparedLine withVerification(String verificationCode) {
            return new PreparedLine(code, processes, quantity, weight, verificationCode);
        }
    }
}
