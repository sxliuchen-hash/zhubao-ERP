package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ChildLineAllocation;
import com.zhubao.erp.business.domain.ChildWorkOrderView;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderSplitView;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.business.domain.ReceiptLineBalance;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.SplitSourceAllocation;
import com.zhubao.erp.business.domain.VerificationCodes;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 母单拆分：不拆仍用母单号；拆开则生成子单，并把对应数量从待加工转入在制。
 * 带请求编号时，库存、子单和拆分决定进入同一次工作单元。
 */
@Service
public class MotherOrderSplitService {

    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final BusinessWorkUnit workUnit;
    private OrderChangeRepository orderChanges;
    /** 本进程已发出、事务可能尚未提交的子单号。 */
    private final Set<String> issuedChildCodes = ConcurrentHashMap.newKeySet();
    private final Object childCodeLock = new Object();

    public MotherOrderSplitService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService
    ) {
        this(documents, stockLedgerService, null);
    }

    @Autowired
    public MotherOrderSplitService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessWorkUnit workUnit
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
        this.workUnit = workUnit;
    }

    @Autowired(required = false)
    public void setOrderChanges(OrderChangeRepository orderChanges) {
        this.orderChanges = orderChanges;
    }

    public MotherOrderSplitView view(String motherOrderCode) {
        return toView(requireMother(motherOrderCode));
    }

    /**
     * 不拆：生成母单验证码，不移动库存。
     * 未带请求编号时各自提交，只留给既有夹具；正式入口必须使用带编号的重载。
     */
    public MotherOrderSplitView keep(String motherOrderCode, String operator) {
        return applyKeep(motherOrderCode, operator, null);
    }

    /**
     * 带请求编号时，拆分决定同一次提交。
     * 同账号、同请求、同内容回放后重新读取当前视图。
     */
    public MotherOrderSplitView keep(String motherOrderCode, String operator, String requestId) {
        requireRequest(requestId, "选择不拆必须带请求编号");
        requireWorkUnit();
        String actor = operatorOf(operator);
        String code = motherOrderCode == null ? "" : motherOrderCode.trim();
        workUnit.execute(new CommandCall(
                requestId,
                actor,
                "MOTHER_KEEP",
                "MOTHER_ORDER",
                code,
                "decision=KEEP"
        ), session -> {
            applyKeep(code, actor, session);
            return "kept";
        });
        return view(code);
    }

    /** 拆成多张子单。未带请求编号时各自提交，只留给既有夹具。 */
    public MotherOrderSplitView split(SplitMotherOrderCommand command) {
        return applySplit(command, null);
    }

    /**
     * 带请求编号时，库存转移、子单和母单拆分状态同一次提交。
     * 同一请求重试只生效一次；内容变化必须换新编号。
     */
    public MotherOrderSplitView split(SplitMotherOrderCommand command, String requestId) {
        requireRequest(requestId, "拆分子单必须带请求编号");
        requireWorkUnit();
        if (command == null) {
            throw new BusinessException("拆分命令不能为空");
        }
        String actor = operatorOf(command.operator());
        String code = command.motherOrderCode() == null ? "" : command.motherOrderCode().trim();
        workUnit.execute(new CommandCall(
                requestId,
                actor,
                "MOTHER_SPLIT",
                "MOTHER_ORDER",
                code,
                splitPayload(command.lines())
        ), session -> {
            applySplit(new SplitMotherOrderCommand(code, actor, command.lines()), session);
            return "split";
        });
        return view(code);
    }

    private MotherOrderSplitView applyKeep(String motherOrderCode, String operator, WorkSession session) {
        if (session != null) {
            documents.lockMotherOrder(motherOrderCode == null ? "" : motherOrderCode.trim(), session);
        }
        MotherOrderRecord mother = requireMother(motherOrderCode, session);
        if (mother.sourceRegisterType() != null && mother.sourceRegisterType().startsWith("INTAKE_LINES:")
                && documents.listReceiptLines(mother.code()).stream().map(ReceiptLine::materialCode).distinct().count() > 1) {
            throw new BusinessException("多材质母单请按材质拆成子单，不能整单不拆");
        }
        if (!listChildren(mother.code(), session).isEmpty()) {
            throw new BusinessException("这张母单已经拆成子单，不能再选择不拆");
        }
        MotherSplitState current = findSplit(mother.code(), session).orElse(null);
        if (current != null && MotherSplitState.SPLIT.equals(current.decision())) {
            throw new BusinessException("这张母单已经拆成子单，不能再选择不拆");
        }
        if (current != null && MotherSplitState.KEEP.equals(current.decision()) && !current.verificationCode().isBlank()) {
            return toView(mother, session);
        }
        String verificationCode = VerificationCodes.derive(
                mother.code(),
                candidate -> verificationExists(candidate, session)
        );
        saveSplit(new MotherSplitState(
                mother.code(),
                MotherSplitState.KEEP,
                verificationCode,
                operatorOf(operator),
                RECORD_TIME.format(LocalDateTime.now())
        ), session);
        return toView(mother, session);
    }

    /** 拆成多张子单。数量、重量合计不得超过母单剩余，库存走台账从待加工转入在制。 */
    private MotherOrderSplitView applySplit(SplitMotherOrderCommand command, WorkSession session) {
        if (command == null) {
            throw new BusinessException("拆分命令不能为空");
        }
        if (session != null) {
            documents.lockMotherOrder(command.motherOrderCode() == null ? "" : command.motherOrderCode().trim(), session);
        }
        MotherOrderRecord mother = requireMother(command.motherOrderCode(), session);
        MotherSplitState current = findSplit(mother.code(), session).orElse(null);
        if (current != null && MotherSplitState.KEEP.equals(current.decision())) {
            throw new BusinessException("这张母单已选择不拆，不能再拆子单");
        }
        List<SplitMotherOrderLine> lines = command.lines();
        if (lines.size() < 2) {
            throw new BusinessException("拆成多张至少需要两张子单");
        }
        List<ChildWorkOrderRecord> existing = listChildren(mother.code(), session);
        boolean detailed = mother.sourceRegisterType() != null
                && mother.sourceRegisterType().startsWith("INTAKE_LINES:");
        Map<Integer, ReceiptLine> sourceByNo = new HashMap<>();
        Map<Integer, BigDecimal> allocatedQuantity = new HashMap<>();
        Map<Integer, BigDecimal> allocatedWeight = new HashMap<>();
        if (detailed) {
            for (ReceiptLine source : documents.listReceiptLines(mother.code())) sourceByNo.put(source.lineNo(), source);
            for (ChildLineAllocation prior : documents.listChildAllocations(mother.code(), session)) {
                allocatedQuantity.merge(prior.sourceLineNo(), prior.quantity(), BigDecimal::add);
                allocatedWeight.merge(prior.sourceLineNo(), prior.weight(), BigDecimal::add);
            }
        }
        BigDecimal remainingQuantity = remaining(mother.quantity(), existing, true);
        BigDecimal remainingWeight = remaining(mother.weight(), existing, false);
        BigDecimal requestedQuantity = ZERO;
        BigDecimal requestedWeight = ZERO;
        List<PreparedLine> prepared = new ArrayList<>();
        List<List<SplitSourceAllocation>> allocationGroups = new ArrayList<>();
        for (SplitMotherOrderLine line : lines) {
            if (line == null || line.quantity() == null || line.weight() == null
                    || line.quantity().compareTo(ZERO) <= 0 || line.weight().compareTo(ZERO) <= 0) {
                throw new BusinessException("拆出的数量和重量必须大于 0");
            }
            requestedQuantity = requestedQuantity.add(line.quantity());
            requestedWeight = requestedWeight.add(line.weight());
            if (detailed) {
                if (line.allocations().isEmpty()) throw new BusinessException("请按收货来源明细分配子单");
                BigDecimal lineQuantity = ZERO;
                BigDecimal lineWeight = ZERO;
                Set<Integer> uniqueSources = new HashSet<>();
                for (SplitSourceAllocation allocation : line.allocations()) {
                    ReceiptLine source = sourceByNo.get(allocation.sourceLineNo());
                    if (source == null || !uniqueSources.add(allocation.sourceLineNo())
                            || allocation.quantity() == null || allocation.weight() == null
                            || allocation.quantity().signum() <= 0 || allocation.weight().signum() <= 0) {
                        throw new BusinessException("子单来源明细或分配数量无效");
                    }
                    if (source.quantityUnit() == null || source.quantityUnit().isBlank()) {
                        throw new BusinessException("来源明细单位缺失");
                    }
                    lineQuantity = lineQuantity.add(allocation.quantity());
                    lineWeight = lineWeight.add(allocation.weight());
                    allocatedQuantity.merge(source.lineNo(), allocation.quantity(), BigDecimal::add);
                    allocatedWeight.merge(source.lineNo(), allocation.weight(), BigDecimal::add);
                    if (allocatedQuantity.get(source.lineNo()).compareTo(source.quantity()) > 0
                            || allocatedWeight.get(source.lineNo()).compareTo(source.weight()) > 0) {
                        throw new BusinessException("来源明细剩余数量或重量不足：第 " + source.lineNo() + " 行");
                    }
                }
                if (lineQuantity.compareTo(line.quantity()) != 0 || lineWeight.compareTo(line.weight()) != 0) {
                    throw new BusinessException("子单数量和重量必须等于来源分配合计");
                }
                if (line.allocations().stream().map(item -> sourceByNo.get(item.sourceLineNo()).materialCode())
                        .distinct().count() > 1) {
                    throw new BusinessException("一张子单暂不能跨材质，请按材质分别拆单");
                }
                allocationGroups.add(line.allocations());
            }
            prepared.add(new PreparedLine(
                    "",
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
        Set<String> reservedChildCodes = new HashSet<>();
        List<PreparedLine> numbered = new ArrayList<>();
        for (PreparedLine line : prepared) {
            numbered.add(new PreparedLine(
                    nextChildCode(session, reservedChildCodes),
                    line.processes(),
                    line.quantity(),
                    line.weight(),
                    ""
            ));
        }
        prepared = numbered;
        Set<String> reservedCodes = new HashSet<>();
        List<PreparedLine> withCodes = new ArrayList<>();
        for (PreparedLine line : prepared) {
            String verificationCode = VerificationCodes.derive(
                    line.code(),
                    candidate -> reservedCodes.contains(candidate) || verificationExists(candidate, session)
            );
            reservedCodes.add(verificationCode);
            withCodes.add(line.withVerification(verificationCode));
        }
        String operator = operatorOf(command.operator());
        String createdAt = RECORD_TIME.format(LocalDateTime.now());
        for (int index = 0; index < withCodes.size(); index++) {
            PreparedLine line = withCodes.get(index);
            List<ChildLineAllocation> savedAllocations = new ArrayList<>();
            StockLedgerEntry entry;
            ReceiptLine firstSource = null;
            if (detailed) {
                entry = null;
                for (SplitSourceAllocation allocation : allocationGroups.get(index)) {
                    ReceiptLine source = sourceByNo.get(allocation.sourceLineNo());
                    StockLedgerEntry moved = moveToWip(mother, line.code(), line.processes(), source.materialCode(),
                            allocation.quantity(), allocation.weight(), operator, session);
                    if (entry == null) { entry = moved; firstSource = source; }
                    savedAllocations.add(new ChildLineAllocation(line.code(), mother.code(), source.lineNo(),
                            source.materialCode(), allocation.quantity(), allocation.weight(), moved.code()));
                }
            } else {
                entry = moveToWip(mother, line, operator, session);
            }
            saveChild(new ChildWorkOrderRecord(
                    line.code(),
                    mother.code(),
                    mother.customerId(),
                    blank(mother.customerName()),
                    blank(mother.processingRequirement()),
                    blank(mother.dueDate()),
                    detailed ? firstSource.materialCode() : blank(mother.materialCode()),
                    detailed ? (savedAllocations.size() > 1 ? "多材质" : firstSource.goodsName()) : blank(mother.goodsName()),
                    line.quantity(),
                    blankToDefault(mother.quantityUnit(), "件"),
                    line.weight(),
                    blank(mother.warehouseCode()),
                    WorkshopProcesses.join(line.processes()),
                    line.verificationCode(),
                    entry.code(),
                    operator,
                    createdAt
            ), session);
            for (ChildLineAllocation allocation : savedAllocations) documents.saveChildAllocation(allocation, session);
        }
        saveSplit(new MotherSplitState(
                mother.code(),
                MotherSplitState.SPLIT,
                "",
                operator,
                createdAt
        ), session);
        return toView(mother, session);
    }

    private StockLedgerEntry moveToWip(MotherOrderRecord mother, PreparedLine line, String operator, WorkSession session) {
        return moveToWip(mother, line.code(), line.processes(), mother.materialCode(),
                line.quantity(), line.weight(), operator, session);
    }

    private StockLedgerEntry moveToWip(MotherOrderRecord mother, String childCode, List<String> processes,
                                       String materialCode, BigDecimal quantity, BigDecimal weight,
                                       String operator, WorkSession session) {
        OwnerType ownerType = ownerTypeOf(mother);
        Long ownerCustomerId = mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId();
        StockDimension fromWaiting = new StockDimension(
                ownerType,
                ownerCustomerId,
                mother.customerId(),
                mother.code(),
                null,
                materialCode,
                mother.warehouseCode(),
                StockStatus.RAW_WAITING
        );
        StockDimension toWip = new StockDimension(
                ownerType,
                ownerCustomerId,
                mother.customerId(),
                mother.code(),
                childCode,
                materialCode,
                mother.warehouseCode(),
                StockStatus.WIP
        );
        StockTransferCommand transfer = new StockTransferCommand(
                "work_order_split",
                childCode,
                fromWaiting,
                toWip,
                quantity,
                weight,
                "拆分子单；工序：" + WorkshopProcesses.join(processes) + "；操作人：" + operator
        );
        if (session == null) {
            return stockLedgerService.transfer(transfer);
        }
        return stockLedgerService.transfer(transfer, session);
    }

    private MotherOrderSplitView toView(MotherOrderRecord mother) {
        return toView(mother, null);
    }

    private MotherOrderSplitView toView(MotherOrderRecord mother, WorkSession session) {
        List<ChildWorkOrderRecord> children = listChildren(mother.code(), session);
        List<ReceiptLineBalance> sourceLines = new ArrayList<>();
        if (mother.sourceRegisterType() != null && mother.sourceRegisterType().startsWith("INTAKE_LINES:")) {
            List<ChildLineAllocation> allocations = documents.listChildAllocations(mother.code(), session);
            for (ReceiptLine line : documents.listReceiptLines(mother.code())) {
                BigDecimal usedQuantity = allocations.stream().filter(item -> item.sourceLineNo() == line.lineNo())
                        .map(ChildLineAllocation::quantity).reduce(ZERO, BigDecimal::add);
                BigDecimal usedWeight = allocations.stream().filter(item -> item.sourceLineNo() == line.lineNo())
                        .map(ChildLineAllocation::weight).reduce(ZERO, BigDecimal::add);
                sourceLines.add(new ReceiptLineBalance(line.lineNo(), line.materialCode(), line.materialName(),
                        line.goodsName(), line.quantity(), line.quantityUnit(), line.weight(),
                        line.quantity().subtract(usedQuantity), line.weight().subtract(usedWeight)));
            }
        }
        MotherSplitState state = findSplit(mother.code(), session).orElse(null);
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
                sticker("mother", mother.code()),
                childViews,
                sourceLines
        );
    }

    private String sticker(String scanType, String code) {
        int version = orderChanges == null ? 0 : orderChanges.latestAppliedVersion(code);
        String instance = "LI" + Integer.toHexString(Math.abs((scanType + "|" + code + "|" + version).hashCode()));
        return "JG|" + scanType + "|" + code + "|" + version + "|" + instance;
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
                sticker("work_order", record.code())
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
        return requireMother(motherOrderCode, null);
    }

    private MotherOrderRecord requireMother(String motherOrderCode, WorkSession session) {
        if (motherOrderCode == null || motherOrderCode.isBlank()) {
            throw new BusinessException("缺少母单号");
        }
        String code = motherOrderCode.trim();
        Optional<MotherOrderRecord> found = session == null
                ? documents.findMotherOrderByCode(code)
                : documents.findMotherOrderByCode(code, session);
        return found.orElseThrow(() -> new BusinessException("母单不存在：" + motherOrderCode));
    }

    private String nextChildCode(WorkSession session, Set<String> reserved) {
        synchronized (childCodeLock) {
            String code = OrderCodes.next(
                    "ZD",
                    LocalDate.now(),
                    documents.listAllChildWorkOrders().stream().map(ChildWorkOrderRecord::code).toList(),
                    candidate -> reserved.contains(candidate)
                            || issuedChildCodes.contains(candidate)
                            || childExists(candidate, session)
            );
            reserved.add(code);
            issuedChildCodes.add(code);
            return code;
        }
    }

    private void requireRequest(String requestId, String message) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(message);
        }
    }

    private void requireWorkUnit() {
        if (workUnit == null) {
            throw new BusinessException("拆单回放尚未装配");
        }
    }

    private String splitPayload(List<SplitMotherOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "lines=";
        }
        StringBuilder builder = new StringBuilder();
        for (SplitMotherOrderLine line : lines) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            if (line == null) {
                builder.append("empty");
                continue;
            }
            builder.append("q=").append(number(line.quantity()))
                    .append(";w=").append(number(line.weight()))
                    .append(";p=").append(String.join(",", WorkshopProcesses.normalize(line.processes())))
                    .append(";a=").append(line.allocations().stream().map(allocation ->
                            allocation.sourceLineNo() + ":" + number(allocation.quantity()) + ":"
                                    + number(allocation.weight())).toList());
        }
        return builder.toString();
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private List<ChildWorkOrderRecord> listChildren(String motherOrderCode, WorkSession session) {
        if (session == null) {
            return documents.listChildWorkOrders(motherOrderCode);
        }
        return documents.listChildWorkOrders(motherOrderCode, session);
    }

    private Optional<MotherSplitState> findSplit(String motherOrderCode, WorkSession session) {
        if (session == null) {
            return documents.findMotherSplitState(motherOrderCode);
        }
        return documents.findMotherSplitState(motherOrderCode, session);
    }

    private void saveSplit(MotherSplitState state, WorkSession session) {
        if (session == null) {
            documents.saveMotherSplitState(state);
            return;
        }
        documents.saveMotherSplitState(state, session);
    }

    private void saveChild(ChildWorkOrderRecord record, WorkSession session) {
        if (session == null) {
            documents.saveChildWorkOrder(record);
            return;
        }
        documents.saveChildWorkOrder(record, session);
    }

    private boolean childExists(String code, WorkSession session) {
        if (session == null) {
            return documents.childWorkOrderExists(code);
        }
        return documents.childWorkOrderExists(code, session);
    }

    private boolean verificationExists(String verificationCode, WorkSession session) {
        if (session == null) {
            return documents.verificationCodeExists(verificationCode);
        }
        return documents.verificationCodeExists(verificationCode, session);
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
