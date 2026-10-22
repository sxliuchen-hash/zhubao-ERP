package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.DocumentVisibility;
import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.app.MobileIntakeService;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherRequirementSource;
import com.zhubao.erp.business.domain.MotherRequirementSourceRepository;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.OrderCodes;
import com.zhubao.erp.business.domain.RequirementText;
import com.zhubao.erp.business.domain.ReceiptLine;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockDocument;
import com.zhubao.erp.inventory.domain.StockDocumentKinds;
import com.zhubao.erp.inventory.domain.StockDocumentLine;
import com.zhubao.erp.inventory.domain.StockDocumentRepository;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公司自有物料和客户寄存备料的正式单据。
 * 库存仍只经台账增减；领用登记不等于已经消耗，也不把公司料并入客户加工库存。
 */
@Service
public class MaterialStockService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ObjectMapper PAYLOADS = new ObjectMapper();
    private final Set<String> reserved = ConcurrentHashMap.newKeySet();
    private final StockLedgerService stocks;
    private final StockDocumentRepository stockDocuments;
    private final BusinessDocumentRepository orders;
    private final BusinessWorkUnit workUnit;
    private final BusinessOperationService operations;
    private final MobileIntakeService intakes;
    private final MotherRequirementSourceRepository requirementSources;
    private final PermissionGuard permissions;
    private final AuthUserAccountRepository accounts;
    private final BusinessDataAccess dataAccess;
    private final FinanceRepository finance;
    private final MasterDataService masterData;

    public MaterialStockService(
            StockLedgerService stocks,
            StockDocumentRepository stockDocuments,
            BusinessDocumentRepository orders,
            BusinessWorkUnit workUnit,
            BusinessOperationService operations,
            MobileIntakeService intakes,
            MotherRequirementSourceRepository requirementSources,
            PermissionGuard permissions
    ) {
        this(stocks, stockDocuments, orders, workUnit, operations, intakes, requirementSources, permissions, null, null, null, null);
    }

    @Autowired
    public MaterialStockService(StockLedgerService stocks, StockDocumentRepository stockDocuments,
            BusinessDocumentRepository orders, BusinessWorkUnit workUnit, BusinessOperationService operations,
            MobileIntakeService intakes, MotherRequirementSourceRepository requirementSources,
            PermissionGuard permissions, AuthUserAccountRepository accounts, BusinessDataAccess dataAccess, FinanceRepository finance, MasterDataService masterData) {
        this.stocks = stocks;
        this.stockDocuments = stockDocuments;
        this.orders = orders;
        this.workUnit = workUnit;
        this.operations = operations;
        this.intakes = intakes;
        this.requirementSources = requirementSources;
        this.permissions = permissions;
        this.accounts = accounts;
        this.dataAccess = dataAccess == null ? new BusinessDataAccess(orders, stocks, accounts) : dataAccess;
        this.finance = finance;
        this.masterData = masterData;
    }

    public StockDesk.Overview overview(StockDesk.Query query, AuthUserAccount account) {
        List<StockDesk.BatchCard> cards = cards(query, account);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal weight = BigDecimal.ZERO;
        boolean weighed = false;
        int frozen = 0;
        int pending = 0;
        int posted = 0;
        for (StockDesk.BatchCard card : cards) {
            if (card.pending()) {
                pending++;
                continue;
            }
            posted++;
            if (positive(card.frozenQuantity()) || positive(card.frozenWeight())) {
                frozen++;
            }
            String unit = card.quantityUnit().isBlank() ? "单位未记录" : card.quantityUnit();
            totals.merge(unit, decimal(card.bookQuantity()), BigDecimal::add);
            if (!card.bookWeight().isBlank()) {
                weight = weight.add(decimal(card.bookWeight()));
                weighed = true;
            }
        }
        List<StockDesk.UnitTotal> quantities = totals.entrySet().stream()
                .map(entry -> new StockDesk.UnitTotal(entry.getKey(), plain(entry.getValue())))
                .toList();
        return new StockDesk.Overview(normalizeScope(query.scope()), posted, quantities, weighed ? plain(weight) : "", frozen, pending);
    }

    public List<StockDesk.BatchCard> cards(StockDesk.Query query, AuthUserAccount account) {
        String scope = normalizeScope(query == null ? "" : query.scope());
        assertRead(scope, account);
        if (query != null && query.customerId() != null && !"company".equals(scope)) {
            assertCustomer(account, query.customerId());
        }
        List<StockDesk.BatchCard> cards = new ArrayList<>();
        if ("processing".equals(scope)) {
            cards.addAll(processingCards(account));
        } else {
            cards.addAll(groupedCards(scope, account));
            cards.addAll(pendingCards(scope, account));
        }
        return cards.stream().filter(card -> matches(card, query)).toList();
    }

    public List<StockDesk.MovementRow> movements(StockDesk.Query query, AuthUserAccount account) {
        if (query == null) {
            throw new BusinessException("请选择要查看的批次");
        }
        String scope = normalizeScope(query.scope());
        assertRead(scope, account);
        if (query.documentCode() != null && !query.documentCode().isBlank()) {
            StockDocument document = stockDocuments.find(query.documentCode().trim())
                    .orElseThrow(() -> new BusinessException("库存单据不存在"));
            assertDocumentVisible(document, account);
            if (StockDocumentKinds.PENDING.equals(document.status())) {
                if ("company".equals(scope) != OwnerType.COMPANY.name().equals(document.ownerType())) throw new BusinessException("库存单据与所选权属范围不一致");
                return List.of(new StockDesk.MovementRow(document.createdAt(), "待核收货", document.documentCode(),
                        "尚未入账", "", plain(sum(document, true)), weightText(sumWeight(document)), "", "", document.operatorName()));
            }
        }
        String batch = text(query.batchCode());
        String mother = text(query.motherOrderCode());
        if (batch.isEmpty() && mother.isEmpty()) {
            throw new BusinessException("请选择批次或加工单");
        }
        Map<StockDimension, BigDecimal> running = new LinkedHashMap<>();
        List<StockDesk.MovementRow> rows = new ArrayList<>();
        List<StockLedgerEntry> ledgers = new ArrayList<>(stocks.ledgers());
        ledgers.sort(Comparator.comparing(StockLedgerEntry::bizTime).thenComparing(StockLedgerEntry::code));
        for (StockLedgerEntry entry : ledgers) {
            if (!touches(entry, batch, mother)) {
                continue;
            }
            StockDimension visibleSide = entry.to() == null ? entry.from() : entry.to();
            if (!entryVisible(entry, account) || visibleSide == null
                    || "company".equals(scope) && visibleSide.ownerType() != OwnerType.COMPANY
                    || "prep".equals(scope) && visibleSide.ownerType() != OwnerType.CUSTOMER) {
                continue;
            }
            BigDecimal before = book(running, batch, mother);
            apply(running, entry.from(), entry.quantity().negate());
            apply(running, entry.to(), entry.quantity());
            BigDecimal after = book(running, batch, mother);
            StockDocument document = stockDocuments.list().stream()
                    .filter(item -> item.documentCode().equals(entry.documentCode()))
                    .findFirst().orElse(null);
            rows.add(new StockDesk.MovementRow(
                    entry.bizTime().toString(),
                    document == null ? kindLabel(entry.documentType()) : kindLabel(document.kind()),
                    entry.documentCode(),
                    side(entry.from()),
                    side(entry.to()),
                    signed(entry, batch, mother),
                    plain(entry.weight()),
                    plain(before),
                    plain(after),
                    document == null ? "" : document.operatorName()
            ));
        }
        return rows;
    }

    public String receiveCompany(StockDesk.ReceiptInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_COMPANY_RECEIVE, account);
        return receive(input, account, true);
    }

    public StockDocument document(String code, AuthUserAccount account) {
        requireAccount(account);
        StockDocument document = stockDocuments.find(text(code)).orElseThrow(() -> new BusinessException("库存单据不存在"));
        assertRead(OwnerType.COMPANY.name().equals(document.ownerType()) ? "company" : "prep", account);
        assertDocumentVisible(document, account);
        return document;
    }

    public String receivePrep(StockDesk.ReceiptInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_PREP_RECEIVE, account);
        return receive(input, account, false);
    }

    public String confirmReceipt(StockDesk.ConfirmInput input, AuthUserAccount account) {
        requireAccount(account);
        requireInput(input);
        return run(input.requestId(), account, "STOCK_CONFIRM", input.documentCode(), confirmPayload(input), session -> {
            StockDocument document = stockDocuments.lock(text(input.documentCode()), session)
                    .orElseThrow(() -> new BusinessException("收货单不存在"));
            if (StockDocumentKinds.COMPANY_RECEIPT.equals(document.kind())) {
                assertPermission(PermissionCode.STOCK_COMPANY_RECEIVE, account);
            } else if (!StockDocumentKinds.PREP_RECEIPT.equals(document.kind())) {
                throw new BusinessException("只有收货单可以确认入账");
            } else {
                assertPermission(PermissionCode.STOCK_PREP_RECEIVE, account);
            }
            assertDocumentVisible(document, account);
            if (StockDocumentKinds.POSTED.equals(document.status())) {
                throw new BusinessException("收货单已经入账");
            }
            if (input.lines() == null || input.lines().size() != document.lines().size()) {
                throw new BusinessException("确认明细必须与收货单行数一致");
            }
            List<StockDocumentLine> posted = new ArrayList<>();
            for (int index = 0; index < document.lines().size(); index++) {
                StockDocumentLine original = document.lines().get(index);
                StockDesk.LineInput actual = input.lines().get(index);
                if (actual == null) throw new BusinessException("请核对每条实收明细");
                if (!original.materialCode().equals(text(actual.materialCode())) || !original.quantityUnit().equals(text(actual.quantityUnit()))) {
                    throw new BusinessException("确认时不能改物料或数量单位，请另开收货单");
                }
                requirePostedAmount(actual.quantity(), actual.weight());
                String batch = allocate("PC");
                StockDimension to = bucket(document.ownerType(), document.customerId(), original.materialCode(), document.warehouseCode(), StockStatus.RAW_WAITING, batch);
                StockLedgerEntry entry = stocks.transfer(new StockTransferCommand(ledgerType(document.kind()), document.documentCode(),
                        null, to, actual.quantity(), actual.weight(), "确认入账第 " + original.lineNo() + " 行"), session);
                posted.add(new StockDocumentLine(original.lineNo(), original.materialCode(), original.quantityUnit(),
                        actual.quantity(), actual.weight(), batch, original.externalRef(), null, true,
                        original.declaredQuantity(), original.declaredWeight(), entry.code()));
            }
            stockDocuments.save(copy(document, StockDocumentKinds.POSTED, posted), session);
            return document.documentCode();
        });
    }

    public String issueCompany(StockDesk.IssueInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_COMPANY_ISSUE, account);
        requireIssue(input, false);
        String purpose = required(input.purpose(), "请填写用途");
        String recipient = required(input.recipient(), "请填写领用人");
        String orderCode = text(input.targetOrderCode());
        if (!orderCode.isEmpty()) {
            assertOrderVisible(orderCode, account);
        }
        return run(input.requestId(), account, "COMPANY_ISSUE_DOC", input.batchCode(), issuePayload(input), session ->
                moveBetween(account, OwnerType.COMPANY, null, "", input, StockStatus.RAW_WAITING, StockStatus.WIP,
                        StockDocumentKinds.COMPANY_ISSUE, "LY", "company_issue", "公司料领用",
                        purpose, recipient, orderCode, "", session));
    }

    public String returnCompany(StockDesk.ReturnInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_COMPANY_RETURN, account);
        return returnFromSource(input, account, OwnerType.COMPANY, null, StockStatus.WIP, StockStatus.RAW_WAITING,
                StockDocumentKinds.COMPANY_ISSUE, StockDocumentKinds.COMPANY_RETURN, "TH", "company_return", "公司料退回");
    }

    public String issuePrep(StockDesk.IssueInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_PREP_ISSUE, account);
        requireIssue(input, true);
        MotherOrderRecord mother = requireMother(input.targetOrderCode(), input.customerId(), input.materialCode(), input.quantityUnit());
        assertCustomer(account, input.customerId());
        return run(input.requestId(), account, "PREP_ISSUE_DOC", mother.code(), issuePayload(input), session ->
                moveOntoMother(account, input, mother, session));
    }

    public String returnPrep(StockDesk.ReturnInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_PREP_RETURN, account);
        requireReturn(input, true);
        assertCustomer(account, input.customerId());
        return run(input.requestId(), account, "PREP_RETURN_DOC", input.sourceDocumentCode(), returnPayload(input), session -> {
            StockDocument issue = requireSource(input.sourceDocumentCode(), StockDocumentKinds.PREP_ISSUE, session);
            assertDocumentVisible(issue, account);
            assertReturnable(issue, input, session);
            requireMother(issue.targetOrderCode(), input.customerId(), input.materialCode(), unitOf(issue, input.sourceLineNo()));
            assertOrderVisible(issue.targetOrderCode(), account);
            orders.lockMotherOrder(issue.targetOrderCode(), session);
            MotherOrderRecord mother = orders.findMotherOrderByCode(issue.targetOrderCode(), session)
                    .orElseThrow(() -> new BusinessException("加工母单不存在"));
            adjustMother(mother, input.quantity().negate(), input.weight().negate(),
                    issue.lines().stream().filter(line -> line.lineNo() == input.sourceLineNo()).findFirst().orElseThrow().ledgerCode(), session);
            StockDimension from = new StockDimension(OwnerType.CUSTOMER, input.customerId(), input.customerId(), mother.code(), null,
                    input.materialCode(), mother.warehouseCode(), StockStatus.RAW_WAITING);
            StockDimension to = bucket(OwnerType.CUSTOMER.name(), input.customerId(), input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, input.batchCode());
            String code = allocate("RB");
            StockLedgerEntry entry = stocks.transfer(new StockTransferCommand("prep_return", code, from, to, input.quantity(), input.weight(), "备料从加工单退回"), session);
            saveMovement(code, StockDocumentKinds.PREP_RETURN, OwnerType.CUSTOMER.name(), input.customerId(), "", input.warehouseCode(),
                    "", "", "", mother.code(), issue.documentCode(), account, input.requestId(), StockDocumentKinds.IN,
                    lineOf(input, entry.code()), session);
            return code;
        });
    }

    public String returnToCustomer(StockDesk.CustomerReturnInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_PREP_CUSTOMER_RETURN, account);
        if (input == null) {
            throw new BusinessException("请填写退还内容");
        }
        assertCustomer(account, input.customerId());
        required(input.handoverPerson(), "请填写接收或交接人");
        requirePostedAmount(input.quantity(), input.weight());
        StockDocumentLine source = requireReceiptLine(OwnerType.CUSTOMER, input.customerId(), input.batchCode(), input.materialCode());
        StockDocument origin = stockDocuments.find(sourceDocumentOf(source)).orElseThrow(() -> new BusinessException("来源收货单不存在"));
        assertDocumentVisible(origin, account);
        return run(input.requestId(), account, "PREP_CUSTOMER_RETURN", input.batchCode(), customerReturnPayload(input), session -> {
            stockDocuments.lock(sourceDocumentOf(source), session).orElseThrow(() -> new BusinessException("来源收货单不存在"));
            String code = allocate("TC");
            StockDimension from = bucket(OwnerType.CUSTOMER.name(), input.customerId(), input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, input.batchCode());
            stocks.depart(new StockTransferCommand("prep_customer_return", code, from, from, input.quantity(), input.weight(), "备料退还客户"), source.ledgerCode(), session);
            saveMovement(code, StockDocumentKinds.PREP_CUSTOMER_RETURN, OwnerType.CUSTOMER.name(), input.customerId(), "",
                    input.warehouseCode(), text(input.sourceNote()), input.handoverPerson(), "", "", sourceDocumentOf(source),
                    account, input.requestId(), StockDocumentKinds.OUT, lineOf(input.materialCode(), source.quantityUnit(), input.quantity(), input.weight(), input.batchCode(), "", null, ""), session);
            return code;
        });
    }

    public String openOrder(StockDesk.OpenOrderInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_PREP_ISSUE, account);
        if (input == null) {
            throw new BusinessException("请填写加工单内容");
        }
        assertCustomer(account, input.customerId());
        required(input.goodsName(), "请填写加工货品");
        required(input.processingRequirement(), "加工要求不能为空");
        RequirementText.requireWithinLimit(input.processingRequirement());
        LocalDate due = parseDate(input.dueDate(), "完成期限需使用 yyyy-MM-dd");
        requirePostedAmount(input.quantity(), input.weight());
        required(input.batchCode(), "请选择来源批次");
        required(input.quantityUnit(), "请填写数量单位");
        StockDocumentLine source = requireReceiptLine(OwnerType.CUSTOMER, input.customerId(), input.batchCode(), input.materialCode());
        StockDocument origin = stockDocuments.find(sourceDocumentOf(source)).orElseThrow(() -> new BusinessException("来源收货单不存在"));
        assertDocumentVisible(origin, account);
        String customerName = masterData == null ? origin.customerName() : masterData.customerForIntake(input.customerId()).name();
        if (!source.quantityUnit().equals(input.quantityUnit().trim())) {
            throw new BusinessException("领用单位必须与寄存批次一致");
        }
        return run(input.requestId(), account, "PREP_OPEN_ORDER", input.batchCode(), openPayload(input), session -> {
            String motherCode = intakes.reserveMotherOrderCode(session);
            StockDimension from = bucket(OwnerType.CUSTOMER.name(), input.customerId(), input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, input.batchCode());
            StockDimension to = new StockDimension(OwnerType.CUSTOMER, input.customerId(), input.customerId(), motherCode, null,
                    input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING);
            String issueCode = allocate("BL");
            StockLedgerEntry entry = stocks.transfer(new StockTransferCommand("prep_issue", issueCode, from, to, input.quantity(), input.weight(), "备料领用建加工单"), session);
            operations.saveProcessingMotherFromPrep(new ConfirmInboundCommand(
                    motherCode, input.customerId(), input.customerId(), input.materialCode(), LocalDate.now().toString(),
                    "", "", "", account.operator(), account.operator(), input.goodsName().trim(), "PREP_ISSUE", null, "",
                    input.warehouseCode(), input.quantity(), input.quantityUnit().trim(), input.weight(), account.operator(),
                    "", "备料领用建单，不重复收货", input.processingRequirement().trim(), due.toString(), customerName
            ), entry, session);
            saveMovement(issueCode, StockDocumentKinds.PREP_ISSUE, OwnerType.CUSTOMER.name(), input.customerId(), customerName,
                    input.warehouseCode(), "", "", "建加工单", motherCode, source == null ? "" : sourceDocumentOf(source),
                    account, input.requestId(), StockDocumentKinds.OUT,
                    lineOf(input.materialCode(), input.quantityUnit(), input.quantity(), input.weight(), input.batchCode(), "", source == null ? null : source.lineNo(), entry.code()),
                    session);
            if (!text(input.templateCode()).isEmpty()) {
                requirementSources.save(new MotherRequirementSource(motherCode, input.templateCode().trim(), input.templateVersion(), input.processingRequirement().trim()), session);
            }
            return motherCode;
        });
    }

    public String freeze(StockDesk.HoldInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_FREEZE, account);
        return hold(input, account, true);
    }

    public String unfreeze(StockDesk.HoldInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_FREEZE, account);
        return hold(input, account, false);
    }

    public String correct(StockDesk.CorrectionInput input, AuthUserAccount account) {
        assertPermission(PermissionCode.STOCK_CORRECT, account);
        if (input == null) {
            throw new BusinessException("请填写冲正内容");
        }
        String direction = text(input.direction());
        if (!StockDocumentKinds.IN.equals(direction) && !StockDocumentKinds.OUT.equals(direction)) {
            throw new BusinessException("请选择冲正是增加还是减少");
        }
        required(input.reason(), "请填写冲正原因");
        requirePostedAmount(input.quantity(), input.weight());
        OwnerType owner = ownerOf(input.scope(), input.customerId(), account);
        StockDocumentLine source = requireReceiptLine(owner, input.customerId(), input.batchCode(), input.materialCode());
        if (!text(input.quantityUnit()).isEmpty() && !source.quantityUnit().equals(text(input.quantityUnit()))) {
            throw new BusinessException("冲正单位必须与来源批次一致");
        }
        return run(input.requestId(), account, "STOCK_CORRECT", input.batchCode(), correctionPayload(input), session -> {
            StockDocument original = stockDocuments.lock(sourceDocumentOf(source), session)
                    .orElseThrow(() -> new BusinessException("来源收货单不存在"));
            assertDocumentVisible(original, account);
            assertSourceDimension(original, source, owner, input.customerId(), input.warehouseCode(), input.batchCode(), input.materialCode());
            String code = allocate("CZ");
            StockDimension bucket = bucket(owner.name(), input.customerId(), input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, input.batchCode());
            String ledger;
            if (StockDocumentKinds.OUT.equals(direction)) {
                ledger = stocks.depart(new StockTransferCommand("stock_correction", code, bucket, bucket, input.quantity(), input.weight(), input.reason().trim()), source.ledgerCode(), session).code();
            } else {
                ledger = stocks.transfer(new StockTransferCommand("stock_correction", code, null, bucket, input.quantity(), input.weight(), input.reason().trim()), session).code();
            }
            saveMovement(code, StockDocumentKinds.CORRECTION, owner.name(), owner == OwnerType.COMPANY ? null : input.customerId(), "",
                    input.warehouseCode(), "", "", input.reason().trim(), "", sourceDocumentOf(source), account, input.requestId(), direction,
                    lineOf(input.materialCode(), text(input.quantityUnit()).isEmpty() ? source.quantityUnit() : input.quantityUnit(), input.quantity(), input.weight(), input.batchCode(), "", source.lineNo(), ledger),
                    session);
            return code;
        });
    }

    private String receive(StockDesk.ReceiptInput input, AuthUserAccount account, boolean company) {
        requireReceipt(input, company);
        if (!company) {
            assertCustomer(account, input.customerId());
        }
        String customerName = company ? "" : masterData == null ? text(input.customerName()) : masterData.customerForIntake(input.customerId()).name();
        boolean postNow = input.postNow();
        if (postNow) {
            for (StockDesk.LineInput line : input.lines()) {
                requirePostedAmount(line.quantity(), line.weight());
            }
        }
        String action = company ? "COMPANY_RECEIPT_DOC" : "PREP_RECEIPT_DOC";
        return run(input.requestId(), account, action, text(input.warehouseCode()), receiptPayload(input), session -> {
            String code = allocate(company ? "GS" : "JC");
            List<StockDocumentLine> lines = new ArrayList<>();
            int number = 1;
            for (StockDesk.LineInput line : input.lines()) {
                String batch = "";
                String ledger = "";
                if (postNow) {
                    batch = allocate("PC");
                    StockDimension to = bucket(company ? OwnerType.COMPANY.name() : OwnerType.CUSTOMER.name(), company ? null : input.customerId(),
                            line.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, batch);
                    ledger = stocks.transfer(new StockTransferCommand(company ? "company_receipt" : "prep_receipt", code, null, to,
                            line.quantity(), line.weight(), "收货第 " + number + " 行"), session).code();
                }
                lines.add(new StockDocumentLine(number, line.materialCode().trim(), line.quantityUnit().trim(),
                        line.quantity(), line.weight(), batch, text(line.externalRef()), null, postNow,
                        line.quantity(), line.weight(), ledger));
                number++;
            }
            StockDocument document = new StockDocument(code, company ? StockDocumentKinds.COMPANY_RECEIPT : StockDocumentKinds.PREP_RECEIPT,
                    postNow ? StockDocumentKinds.POSTED : StockDocumentKinds.PENDING,
                    company ? OwnerType.COMPANY.name() : OwnerType.CUSTOMER.name(),
                    company ? null : input.customerId(), customerName, input.warehouseCode().trim(),
                    text(input.sourceNote()), text(input.handoverPerson()), "", "", "", "",
                    clip(account.operator()), input.requestId().trim(), parseDate(input.bizDate(), "业务日期需使用 yyyy-MM-dd").toString(),
                    STAMP.format(LocalDateTime.now()), text(input.evidenceCode()), StockDocumentKinds.IN, lines);
            stockDocuments.save(document, session);
            return code;
        });
    }

    private String moveBetween(AuthUserAccount account, OwnerType owner, Long customerId, String customerName, StockDesk.IssueInput input,
                               StockStatus fromStatus, StockStatus toStatus, String kind, String prefix, String ledgerType, String remark,
                               String purpose, String recipient, String orderCode, String sourceCode, WorkSession session) {
        StockDocumentLine receipt = requireReceiptLine(owner, customerId, input.batchCode(), input.materialCode());
        stockDocuments.find(sourceDocumentOf(receipt)).ifPresent(document -> assertDocumentVisible(document, account));
        if (!receipt.quantityUnit().equals(text(input.quantityUnit()))) {
            throw new BusinessException("领用单位必须与入库批次一致");
        }
        String code = allocate(prefix);
        StockDimension from = bucket(owner.name(), customerId, input.materialCode(), input.warehouseCode(), fromStatus, input.batchCode());
        StockDimension to = bucket(owner.name(), customerId, input.materialCode(), input.warehouseCode(), toStatus, input.batchCode());
        StockLedgerEntry entry = stocks.transfer(new StockTransferCommand(ledgerType, code, from, to, input.quantity(), input.weight(), remark), session);
        saveMovement(code, kind, owner.name(), customerId, customerName, input.warehouseCode(), "", recipient, purpose, orderCode,
                sourceCode.isEmpty() && receipt != null ? sourceDocumentOf(receipt) : sourceCode, account, input.requestId(), StockDocumentKinds.OUT,
                lineOf(input.materialCode(), input.quantityUnit(), input.quantity(), input.weight(), input.batchCode(), "", receipt == null ? null : receipt.lineNo(), entry.code()),
                session);
        return code;
    }

    private String moveOntoMother(AuthUserAccount account, StockDesk.IssueInput input, MotherOrderRecord mother, WorkSession session) {
        assertOrderVisible(mother.code(), account);
        orders.lockMotherOrder(mother.code(), session);
        mother = orders.findMotherOrderByCode(mother.code(), session).orElseThrow(() -> new BusinessException("加工母单不存在"));
        if (orders.listReceiptLines(mother.code()).stream().anyMatch(line -> !line.materialCode().equals(input.materialCode()) || !line.quantityUnit().equals(input.quantityUnit()))) {
            throw new BusinessException("多材质或混合单位加工单必须按来源明细办理，不能直接补料");
        }
        StockDocumentLine receipt = requireReceiptLine(OwnerType.CUSTOMER, input.customerId(), input.batchCode(), input.materialCode());
        if (!receipt.quantityUnit().equals(text(input.quantityUnit()))) throw new BusinessException("领用单位必须与来源批次一致");
        String code = allocate("BL");
        StockDimension from = bucket(OwnerType.CUSTOMER.name(), input.customerId(), input.materialCode(), input.warehouseCode(), StockStatus.RAW_WAITING, input.batchCode());
        StockDimension to = new StockDimension(OwnerType.CUSTOMER, input.customerId(), input.customerId(), mother.code(), null,
                input.materialCode(), mother.warehouseCode(), StockStatus.RAW_WAITING);
        StockLedgerEntry entry = stocks.transfer(new StockTransferCommand("prep_issue", code, from, to, input.quantity(), input.weight(), "备料领用到加工单"), session);
        adjustMother(mother, input.quantity(), input.weight(), "", session);
        int nextLine = orders.listStoredReceiptLines(mother.code(), session).stream().mapToInt(ReceiptLine::lineNo).max().orElse(0) + 1;
        if (nextLine == 1 && mother.quantity().signum() > 0) {
            orders.saveReceiptLine(mother.code(), new ReceiptLine(1, mother.materialCode(), mother.materialCode(), mother.goodsName(),
                    mother.quantity(), mother.quantityUnit(), mother.weight(), mother.ledgerCode()), session);
            nextLine = 2;
        }
        orders.saveReceiptLine(mother.code(), new ReceiptLine(nextLine, input.materialCode(), input.materialCode(), mother.goodsName(),
                input.quantity(), input.quantityUnit(), input.weight(), entry.code()), session);
        saveMovement(code, StockDocumentKinds.PREP_ISSUE, OwnerType.CUSTOMER.name(), input.customerId(), mother.customerName(),
                input.warehouseCode(), "", text(input.recipient()), text(input.purpose()), mother.code(),
                receipt == null ? "" : sourceDocumentOf(receipt), account, input.requestId(), StockDocumentKinds.OUT,
                lineOf(input.materialCode(), input.quantityUnit(), input.quantity(), input.weight(), input.batchCode(), "", receipt == null ? null : receipt.lineNo(), entry.code()),
                session);
        return code;
    }

    /** 尚未开工或入财务时，领用与退回同步加工数量；已有下游事实须走改单。 */
    private void adjustMother(MotherOrderRecord mother, BigDecimal quantityDelta, BigDecimal weightDelta, String sourceLedger, WorkSession session) {
        if (orders.motherHasDownstream(mother.code(), session) || finance != null && finance.hasMotherPosting(mother.code())) {
            throw new BusinessException("加工单已有拆单、交付、发货或财务记录，请走改单，不能直接领入或退料");
        }
        BigDecimal quantity = mother.quantity().add(quantityDelta);
        BigDecimal weight = mother.weight().add(weightDelta);
        if (quantity.signum() < 0 || weight.signum() < 0 || (quantity.signum() == 0) != (weight.signum() == 0)) {
            throw new BusinessException("退回后加工单数量和重量不匹配");
        }
        if (!sourceLedger.isBlank()) {
            List<ReceiptLine> lines = orders.listStoredReceiptLines(mother.code(), session);
            if (!lines.isEmpty()) {
                ReceiptLine line = lines.stream().filter(item -> sourceLedger.equals(item.ledgerCode())).findFirst()
                        .orElseThrow(() -> new BusinessException("领用来源无法对应加工明细，不能退回"));
                BigDecimal remainingQuantity = line.quantity().add(quantityDelta);
                BigDecimal remainingWeight = line.weight().add(weightDelta);
                if (remainingQuantity.signum() < 0 || remainingWeight.signum() < 0) throw new BusinessException("退回超过加工明细剩余量");
                orders.replaceReceiptLine(mother.code(), new ReceiptLine(line.lineNo(), line.materialCode(), line.materialName(), line.goodsName(),
                        remainingQuantity, line.quantityUnit(), remainingWeight, line.ledgerCode()), session);
            }
        }
        orders.updateMotherOrderSummary(mother.code(), quantity, weight, session);
    }

    private String returnFromSource(StockDesk.ReturnInput input, AuthUserAccount account, OwnerType owner, Long customerId,
                                    StockStatus fromStatus, StockStatus toStatus, String sourceKind, String kind, String prefix,
                                    String ledgerType, String remark) {
        requireReturn(input, false);
        if (customerId != null) {
            assertCustomer(account, customerId);
        }
        return run(input.requestId(), account, kind, input.sourceDocumentCode(), returnPayload(input), session -> {
            StockDocument source = requireSource(input.sourceDocumentCode(), sourceKind, session);
            assertDocumentVisible(source, account);
            assertReturnable(source, input, session);
            String code = allocate(prefix);
            StockDimension from = bucket(owner.name(), customerId, input.materialCode(), input.warehouseCode(), fromStatus, input.batchCode());
            StockDimension to = bucket(owner.name(), customerId, input.materialCode(), input.warehouseCode(), toStatus, input.batchCode());
            StockLedgerEntry entry = stocks.transfer(new StockTransferCommand(ledgerType, code, from, to, input.quantity(), input.weight(), remark), session);
            saveMovement(code, kind, owner.name(), customerId, "", input.warehouseCode(), "", "", "", "", source.documentCode(),
                    account, input.requestId(), StockDocumentKinds.IN, lineOf(input, entry.code()), session);
            return code;
        });
    }

    private String hold(StockDesk.HoldInput input, AuthUserAccount account, boolean freeze) {
        if (input == null) {
            throw new BusinessException("请填写冻结内容");
        }
        requirePostedAmount(input.quantity(), input.weight());
        OwnerType owner = ownerOf(input.scope(), input.customerId(), account);
        StockDocumentLine receipt = requireReceiptLine(owner, input.customerId(), input.batchCode(), input.materialCode());
        stockDocuments.find(sourceDocumentOf(receipt)).ifPresent(document -> assertDocumentVisible(document, account));
        return run(input.requestId(), account, freeze ? "STOCK_FREEZE" : "STOCK_UNFREEZE", input.batchCode(), holdPayload(input), session -> {
            if (!freeze) {
                StockDocument original = requireSource(input.sourceDocumentCode(), StockDocumentKinds.FREEZE, session);
                assertDocumentVisible(original, account);
                assertSourceDimension(original, original.lines().get(0), owner, input.customerId(), input.warehouseCode(), input.batchCode(), input.materialCode());
                BigDecimal[] used = usedAmount(original.documentCode(), StockDocumentKinds.UNFREEZE, 1, session);
                StockDocumentLine line = original.lines().get(0);
                if (used[0].add(input.quantity()).compareTo(line.quantity()) > 0 || used[1].add(nz(input.weight())).compareTo(nz(line.weight())) > 0) {
                    throw new BusinessException("解除冻结不能超过原冻结数量或重量");
                }
            }
            String code = allocate(freeze ? "DJ" : "JF");
            StockStatus from = freeze ? StockStatus.RAW_WAITING : StockStatus.FROZEN;
            StockStatus to = freeze ? StockStatus.FROZEN : StockStatus.RAW_WAITING;
            StockDimension source = bucket(owner.name(), owner == OwnerType.COMPANY ? null : input.customerId(), input.materialCode(), input.warehouseCode(), from, input.batchCode());
            StockDimension target = bucket(owner.name(), owner == OwnerType.COMPANY ? null : input.customerId(), input.materialCode(), input.warehouseCode(), to, input.batchCode());
            StockLedgerEntry entry = stocks.transfer(new StockTransferCommand(freeze ? "stock_freeze" : "stock_unfreeze", code, source, target, input.quantity(), input.weight(), freeze ? "冻结" : "解除冻结"), session);
            String unit = unitOfBatch(owner, input.customerId(), input.batchCode(), input.materialCode());
            saveMovement(code, freeze ? StockDocumentKinds.FREEZE : StockDocumentKinds.UNFREEZE, owner.name(),
                    owner == OwnerType.COMPANY ? null : input.customerId(), "", input.warehouseCode(), "", "", "", "",
                    text(input.sourceDocumentCode()), account, input.requestId(), freeze ? StockDocumentKinds.OUT : StockDocumentKinds.IN,
                    lineOf(input.materialCode(), unit, input.quantity(), input.weight(), input.batchCode(), "", freeze ? null : 1, entry.code()), session);
            return code;
        });
    }

    private List<StockDesk.BatchCard> groupedCards(String scope, AuthUserAccount account) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (StockBalance balance : stocks.balances()) {
            if (!inScope(scope, balance) || !balanceVisible(balance, account) || !positive(balance)) {
                continue;
            }
            StockDimension dimension = balance.dimension();
            String key = dimension.ownerType().name() + "|" + dimension.customerId() + "|" + dimension.materialCode()
                    + "|" + dimension.warehouseCode() + "|" + dimension.batchCode();
            Accumulator group = groups.computeIfAbsent(key, ignored -> new Accumulator(dimension));
            group.add(dimension.stockStatus(), balance.quantity(), balance.weight());
        }
        List<StockDesk.BatchCard> cards = new ArrayList<>();
        for (Accumulator group : groups.values()) {
            StockDocumentLine receipt = receiptLineOrNull(group.dimension.ownerType(), group.dimension.customerId(), group.dimension.batchCode(), group.dimension.materialCode());
            String unit = receipt == null ? "单位未记录" : receipt.quantityUnit();
            String updated = latestTime(group.dimension.batchCode());
            cards.add(new StockDesk.BatchCard(scope, ownerLabel(group.dimension, null), group.dimension.ownerType().name(),
                    group.dimension.customerId(), group.dimension.materialCode(), group.dimension.batchCode(), group.dimension.warehouseCode(),
                    "", "", groupLabel(group), unit, plain(group.bookQty), plain(group.bookWeight), plain(group.frozenQty), plain(group.frozenWeight),
                    plain(group.availableQty), plain(group.availableWeight), plain(group.issuedQty), plain(group.issuedWeight),
                    plain(group.bookWeight), false, receipt == null, receipt == null ? "" : sourceDocumentOf(receipt),
                    receipt == null ? "" : receipt.externalRef(), updated, receipt == null ? 0 : receipt.lineNo()));
        }
        return cards;
    }

    private List<StockDesk.BatchCard> pendingCards(String scope, AuthUserAccount account) {
        String kind = "company".equals(scope) ? StockDocumentKinds.COMPANY_RECEIPT : StockDocumentKinds.PREP_RECEIPT;
        List<StockDesk.BatchCard> cards = new ArrayList<>();
        for (StockDocument document : stockDocuments.list()) {
            if (!kind.equals(document.kind()) || !StockDocumentKinds.PENDING.equals(document.status()) || !documentVisible(document, account)) {
                continue;
            }
            for (StockDocumentLine line : document.lines()) {
                cards.add(new StockDesk.BatchCard(scope, "company".equals(scope) ? "公司自有" : customerLabel(document),
                        document.ownerType(), document.customerId(), line.materialCode(), "", document.warehouseCode(), "", "",
                        "待核", line.quantityUnit(), plain(line.quantity()), line.weight() == null ? "" : plain(line.weight()),
                        "0", "0", "0", "0", "0", "0", line.weight() == null ? "待称量" : plain(line.weight()),
                        true, false, document.documentCode(), line.externalRef(), document.bizDate(), line.lineNo()));
            }
        }
        return cards;
    }

    private List<StockDesk.BatchCard> processingCards(AuthUserAccount account) {
        List<StockDesk.BatchCard> cards = new ArrayList<>();
        for (StockBalance balance : stocks.balances()) {
            StockDimension dimension = balance.dimension();
            if (dimension.motherOrderCode().isBlank() || !positive(balance) || !balanceVisible(balance, account)) {
                continue;
            }
            MotherOrderRecord mother = orders.findMotherOrderByCode(dimension.motherOrderCode()).orElse(null);
            String unit = mother == null || mother.quantityUnit() == null || mother.quantityUnit().isBlank() ? "单位未记录" : mother.quantityUnit();
            boolean frozen = dimension.stockStatus() == StockStatus.FROZEN;
            cards.add(new StockDesk.BatchCard("processing", ownerLabel(dimension, mother), dimension.ownerType().name(),
                    dimension.customerId(), dimension.materialCode(), dimension.batchCode(), dimension.warehouseCode(),
                    dimension.motherOrderCode(), text(dimension.workOrderCode()), statusLabel(dimension.stockStatus()), unit,
                    plain(balance.quantity()), plain(balance.weight()), frozen ? plain(balance.quantity()) : "0",
                    frozen ? plain(balance.weight()) : "0", frozen ? "0" : plain(balance.quantity()), frozen ? "0" : plain(balance.weight()),
                    "0", "0", plain(balance.weight()), false, false, dimension.motherOrderCode(), "", "", 0));
        }
        return cards;
    }

    private void saveMovement(String code, String kind, String ownerType, Long customerId, String customerName, String warehouse,
                              String sourceNote, String handover, String purpose, String target, String sourceDocument,
                              AuthUserAccount account, String requestId, String direction, StockDocumentLine line, WorkSession session) {
        StockDocument document = new StockDocument(code, kind, StockDocumentKinds.POSTED, ownerType, customerId, customerName,
                warehouse.trim(), sourceNote, handover, purpose, handover, target, sourceDocument, clip(account.operator()),
                requestId.trim(), LocalDate.now().toString(), STAMP.format(LocalDateTime.now()), "", direction, List.of(line));
        stockDocuments.save(document, session);
    }

    private StockDocument copy(StockDocument document, String status, List<StockDocumentLine> lines) {
        return new StockDocument(document.documentCode(), document.kind(), status, document.ownerType(), document.customerId(),
                document.customerName(), document.warehouseCode(), document.sourceNote(), document.handoverPerson(), document.purpose(),
                document.recipient(), document.targetOrderCode(), document.sourceDocumentCode(), document.operatorName(),
                document.requestId(), document.bizDate(), document.createdAt(), document.evidenceCode(), document.direction(), lines);
    }

    private StockDimension bucket(String ownerType, Long customerId, String material, String warehouse, StockStatus status, String batch) {
        OwnerType owner = OwnerType.valueOf(ownerType);
        Long customer = owner == OwnerType.COMPANY ? null : customerId;
        return new StockDimension(owner, customer, customer, "", null, material.trim(), warehouse.trim(), status, batch.trim());
    }

    private String allocate(String prefix) {
        synchronized (reserved) {
            List<String> existing = new ArrayList<>(reserved);
            for (StockDocument document : stockDocuments.list()) {
                existing.add(document.documentCode());
                for (StockDocumentLine line : document.lines()) {
                    if (!line.batchCode().isBlank()) {
                        existing.add(line.batchCode());
                    }
                }
            }
            for (StockBalance balance : stocks.balances()) {
                if (!balance.dimension().batchCode().isBlank()) {
                    existing.add(balance.dimension().batchCode());
                }
            }
            String code = OrderCodes.next(prefix, LocalDate.now(), existing, reserved::contains);
            reserved.add(code);
            return code;
        }
    }

    private String run(String requestId, AuthUserAccount account, String action, String target, String payload, BusinessWork work) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException("库存操作必须带请求编号");
        }
        return workUnit.execute(new CommandCall(requestId.trim(), account.operator().trim(), action, "STOCK_DOCUMENT", text(target), payload), work::run);
    }

    private MotherOrderRecord requireMother(String code, Long customerId, String material, String unit) {
        MotherOrderRecord mother = orders.findMotherOrderByCode(text(code))
                .orElseThrow(() -> new BusinessException("加工母单不存在：" + code));
        if (mother.customerId() == null || !mother.customerId().equals(customerId)) {
            throw new PermissionDeniedException("不能把备料领用到其他客户的加工单");
        }
        if (!mother.materialCode().equals(text(material))) {
            throw new BusinessException("备料物料与加工母单材质不一致，不能记到原单");
        }
        if (!text(unit).isEmpty() && !text(mother.quantityUnit()).isEmpty() && !mother.quantityUnit().equals(unit.trim())) {
            throw new BusinessException("数量单位与加工母单不一致");
        }
        return mother;
    }

    private void assertOrderVisible(String code, AuthUserAccount account) {
        dataAccess.assertCanRead(BusinessDataAccess.contextOf(account), code);
        MotherOrderRecord mother = orders.findMotherOrderByCode(code).orElse(null);
        if (mother == null) {
            ChildWorkOrderRecord child = orders.findChildWorkOrderAny(code).orElseThrow(() -> new BusinessException("加工单不存在：" + code));
            mother = orders.findMotherOrderByCode(child.motherOrderCode()).orElseThrow(() -> new BusinessException("加工母单不存在：" + child.motherOrderCode()));
        }
        if (mother.customerId() != null) {
            assertCustomer(account, mother.customerId());
        }
    }

    private StockDocument requireSource(String code, String kind) {
        StockDocument document = stockDocuments.find(text(code)).orElseThrow(() -> new BusinessException("原业务单不存在，不能退回或冲正"));
        if (!kind.equals(document.kind()) || !StockDocumentKinds.POSTED.equals(document.status())) {
            throw new BusinessException("原业务单不存在，不能退回或冲正");
        }
        return document;
    }

    private StockDocument requireSource(String code, String kind, WorkSession session) {
        StockDocument document = stockDocuments.lock(text(code), session)
                .orElseThrow(() -> new BusinessException("原业务单不存在，不能退回或冲正"));
        if (!kind.equals(document.kind()) || !StockDocumentKinds.POSTED.equals(document.status())) {
            throw new BusinessException("原业务单不存在，不能退回或冲正");
        }
        return document;
    }

    private void assertReturnable(StockDocument source, StockDesk.ReturnInput input, WorkSession session) {
        StockDocumentLine line = source.lines().stream().filter(item -> item.lineNo() == input.sourceLineNo()).findFirst()
                .orElseThrow(() -> new BusinessException("原领用明细不存在"));
        assertSourceDimension(source, line, OwnerType.valueOf(source.ownerType()), input.customerId(),
                input.warehouseCode(), input.batchCode(), input.materialCode());
        BigDecimal[] used = usedAmount(source.documentCode(), returnKind(source.kind()), input.sourceLineNo(), session);
        if (used[0].add(input.quantity()).compareTo(line.quantity()) > 0 || used[1].add(nz(input.weight())).compareTo(nz(line.weight())) > 0) {
            throw new BusinessException("退回超过该笔可退数量或重量");
        }
    }

    private String returnKind(String sourceKind) {
        if (StockDocumentKinds.COMPANY_ISSUE.equals(sourceKind)) {
            return StockDocumentKinds.COMPANY_RETURN;
        }
        if (StockDocumentKinds.PREP_ISSUE.equals(sourceKind)) {
            return StockDocumentKinds.PREP_RETURN;
        }
        return "";
    }

    private void assertSourceDimension(StockDocument source, StockDocumentLine line, OwnerType owner,
            Long customerId, String warehouse, String batch, String material) {
        if (!owner.name().equals(source.ownerType())
                || owner == OwnerType.CUSTOMER && !Objects.equals(source.customerId(), customerId)
                || !source.warehouseCode().equals(text(warehouse))
                || !line.batchCode().equals(text(batch)) || !line.materialCode().equals(text(material))) {
            throw new BusinessException("操作必须使用原单的权属、客户、仓库、批次和物料");
        }
    }

    private void assertCustomerReturnable(StockDesk.CustomerReturnInput input, StockDocumentLine source) {
        BigDecimal receivedQty = source.quantity();
        BigDecimal receivedWeight = nz(source.weight());
        BigDecimal outQty = BigDecimal.ZERO;
        BigDecimal outWeight = BigDecimal.ZERO;
        for (StockDocument document : stockDocuments.list()) {
            if (!StockDocumentKinds.POSTED.equals(document.status())) {
                continue;
            }
            for (StockDocumentLine line : document.lines()) {
                if (!input.batchCode().trim().equals(line.batchCode())) {
                    continue;
                }
                if (StockDocumentKinds.PREP_ISSUE.equals(document.kind()) || StockDocumentKinds.PREP_CUSTOMER_RETURN.equals(document.kind())
                        || (StockDocumentKinds.CORRECTION.equals(document.kind()) && StockDocumentKinds.OUT.equals(document.direction()))) {
                    outQty = outQty.add(line.quantity());
                    outWeight = outWeight.add(nz(line.weight()));
                }
                if (StockDocumentKinds.PREP_RETURN.equals(document.kind())
                        || (StockDocumentKinds.CORRECTION.equals(document.kind()) && StockDocumentKinds.IN.equals(document.direction()))) {
                    outQty = outQty.subtract(line.quantity());
                    outWeight = outWeight.subtract(nz(line.weight()));
                }
            }
        }
        if (outQty.add(input.quantity()).compareTo(receivedQty) > 0 || outWeight.add(nz(input.weight())).compareTo(receivedWeight) > 0) {
            throw new BusinessException("退还超过该批次可退数量或重量");
        }
    }

    private BigDecimal[] usedAmount(String sourceCode, String kind, int sourceLineNo, WorkSession session) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (StockDocument document : stockDocuments.related(sourceCode, session)) {
            if (!kind.equals(document.kind()) || !sourceCode.equals(document.sourceDocumentCode()) || !StockDocumentKinds.POSTED.equals(document.status())) {
                continue;
            }
            for (StockDocumentLine line : document.lines()) {
                if (line.sourceLineNo() != null && line.sourceLineNo() == sourceLineNo) {
                    quantity = quantity.add(line.quantity());
                    weight = weight.add(nz(line.weight()));
                }
            }
        }
        return new BigDecimal[]{quantity, weight};
    }

    private StockDocumentLine requireReceiptLine(OwnerType owner, Long customerId, String batch, String material) {
        StockDocumentLine line = receiptLineOrNull(owner, customerId, batch, material);
        if (line == null) {
            throw new BusinessException("这批没有收货来源，不能退还或冲正");
        }
        return line;
    }

    private StockDocumentLine receiptLineOrNull(OwnerType owner, Long customerId, String batch, String material) {
        String kind = owner == OwnerType.COMPANY ? StockDocumentKinds.COMPANY_RECEIPT : StockDocumentKinds.PREP_RECEIPT;
        for (StockDocument document : stockDocuments.list()) {
            if (!kind.equals(document.kind()) || !StockDocumentKinds.POSTED.equals(document.status())) {
                continue;
            }
            if (owner == OwnerType.CUSTOMER && !Objects.equals(document.customerId(), customerId)) {
                continue;
            }
            for (StockDocumentLine line : document.lines()) {
                if (line.batchCode().equals(text(batch)) && line.materialCode().equals(text(material))) {
                    return line;
                }
            }
        }
        return null;
    }

    private String sourceDocumentOf(StockDocumentLine line) {
        for (StockDocument document : stockDocuments.list()) {
            if (document.lines().stream().anyMatch(item -> item == line || (item.batchCode().equals(line.batchCode()) && item.lineNo() == line.lineNo() && item.ledgerCode().equals(line.ledgerCode())))) {
                return document.documentCode();
            }
        }
        return "";
    }

    private String unitOf(StockDocument document, int lineNo) {
        return document.lines().stream().filter(line -> line.lineNo() == lineNo).map(StockDocumentLine::quantityUnit).findFirst().orElse("");
    }

    private String unitOfBatch(OwnerType owner, Long customerId, String batch, String material) {
        StockDocumentLine line = receiptLineOrNull(owner, customerId, batch, material);
        return line == null ? "单位未记录" : line.quantityUnit();
    }

    private String latestTime(String batch) {
        return stockDocuments.list().stream()
                .filter(document -> document.lines().stream().anyMatch(line -> batch.equals(line.batchCode())))
                .map(StockDocument::createdAt)
                .max(String::compareTo)
                .orElse("");
    }

    private boolean inScope(String scope, StockBalance balance) {
        StockDimension dimension = balance.dimension();
        if ("company".equals(scope)) {
            return dimension.ownerType() == OwnerType.COMPANY;
        }
        return dimension.ownerType() == OwnerType.CUSTOMER && !dimension.batchCode().isBlank() && dimension.motherOrderCode().isBlank();
    }

    private boolean matches(StockDesk.BatchCard card, StockDesk.Query query) {
        if (query == null) {
            return true;
        }
        if (query.customerId() != null && !query.customerId().equals(card.customerId())) {
            return false;
        }
        if (!text(query.materialCode()).isEmpty() && !card.materialCode().toLowerCase().contains(query.materialCode().trim().toLowerCase())) {
            return false;
        }
        if (!text(query.batchCode()).isEmpty() && !card.batchCode().contains(query.batchCode().trim())) {
            return false;
        }
        if (!text(query.warehouseCode()).isEmpty() && !card.warehouseCode().equals(query.warehouseCode().trim())) {
            return false;
        }
        if (!text(query.status()).isEmpty() && !card.status().contains(query.status().trim())) {
            return false;
        }
        if (!text(query.fromDate()).isEmpty() || !text(query.toDate()).isEmpty()) {
            String day = card.updatedAt().length() >= 10 ? card.updatedAt().substring(0, 10) : "";
            if (day.isEmpty()) {
                return false;
            }
            if (!text(query.fromDate()).isEmpty() && day.compareTo(query.fromDate().trim()) < 0) {
                return false;
            }
            if (!text(query.toDate()).isEmpty() && day.compareTo(query.toDate().trim()) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean touches(StockLedgerEntry entry, String batch, String mother) {
        if (!batch.isEmpty() && ((entry.from() != null && batch.equals(entry.from().batchCode())) || (entry.to() != null && batch.equals(entry.to().batchCode())))) {
            return true;
        }
        return !mother.isEmpty() && ((entry.from() != null && mother.equals(entry.from().motherOrderCode())) || (entry.to() != null && mother.equals(entry.to().motherOrderCode())) || mother.equals(entry.documentCode()));
    }

    private void apply(Map<StockDimension, BigDecimal> running, StockDimension dimension, BigDecimal delta) {
        if (dimension == null || delta == null) {
            return;
        }
        running.merge(dimension, delta, BigDecimal::add);
    }

    private BigDecimal book(Map<StockDimension, BigDecimal> running, String batch, String mother) {
        return running.entrySet().stream()
                .filter(entry -> !batch.isEmpty() ? batch.equals(entry.getKey().batchCode()) : mother.equals(entry.getKey().motherOrderCode()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String signed(StockLedgerEntry entry, String batch, String mother) {
        boolean incoming = entry.to() != null && (!batch.isEmpty() ? batch.equals(entry.to().batchCode()) : mother.equals(entry.to().motherOrderCode()));
        boolean outgoing = entry.from() != null && (!batch.isEmpty() ? batch.equals(entry.from().batchCode()) : mother.equals(entry.from().motherOrderCode()));
        if (incoming && outgoing) {
            return plain(entry.quantity());
        }
        if (outgoing) {
            return "-" + plain(entry.quantity());
        }
        return plain(entry.quantity());
    }

    private String side(StockDimension dimension) {
        if (dimension == null) {
            return "离厂";
        }
        String place = dimension.batchCode().isBlank() ? dimension.motherOrderCode() : dimension.batchCode();
        return place + " " + statusLabel(dimension.stockStatus());
    }

    private void assertRead(String scope, AuthUserAccount account) {
        requireAccount(account);
        if ("company".equals(scope) && !canReadCompany(account)) {
            throw new PermissionDeniedException("公司自有物料仅仓库、财务、管理员或总经理可以查看");
        }
        if (!"company".equals(scope) && !Set.of(RoleCode.ADMIN, RoleCode.GENERAL_MANAGER, RoleCode.WAREHOUSE_KEEPER,
                RoleCode.RECEIVER, RoleCode.FINANCE_STATISTICIAN, RoleCode.BUSINESS_MANAGER).contains(account.role())) {
            throw new PermissionDeniedException("当前岗位无权查看寄存库存");
        }
    }

    private void assertPermission(PermissionCode permission, AuthUserAccount account) {
        requireAccount(account);
        permissions.assertAllowed(permission, new OperatorContext(account.operator(), account.role(), account.dataScope(), false, "",
                account.customerScope(), account.coverDuties()));
    }

    private void assertCustomer(AuthUserAccount account, Long customerId) {
        if (customerId == null) {
            throw new BusinessException("请选择客户");
        }
        if (!visibleCustomer(account, customerId)) {
            throw new PermissionDeniedException("客户不在当前账号的授权范围内");
        }
    }

    private void assertDocumentVisible(StockDocument document, AuthUserAccount account) {
        if (OwnerType.COMPANY.name().equals(document.ownerType())) {
            if (!canReadCompany(account)) {
                throw new PermissionDeniedException("公司自有物料仅仓库、财务、管理员或总经理可以查看");
            }
        } else {
            assertCustomer(account, document.customerId());
        }
        if (!DocumentVisibility.canSee(BusinessDataAccess.contextOf(account), document.customerId(), document.customerName(),
                "inventory", List.of(text(document.operatorName()), text(document.handoverPerson()), text(document.recipient())),
                accounts == null ? List.of(account) : accounts.findAll())) {
            throw new PermissionDeniedException("库存单据不在当前账号的数据范围内");
        }
    }

    private boolean documentVisible(StockDocument document, AuthUserAccount account) {
        try {
            assertDocumentVisible(document, account);
            return true;
        } catch (PermissionDeniedException ex) {
            return false;
        }
    }

    private boolean balanceVisible(StockBalance balance, AuthUserAccount account) {
        StockDimension dimension = balance.dimension();
        if (dimension.ownerType() == OwnerType.COMPANY && !canReadCompany(account)) return false;
        if (!dimension.motherOrderCode().isBlank()) return dataAccess.canRead(BusinessDataAccess.contextOf(account), dimension);
        StockDocumentLine source = receiptLineOrNull(dimension.ownerType(), dimension.customerId(), dimension.batchCode(), dimension.materialCode());
        if (source != null) return stockDocuments.find(sourceDocumentOf(source)).map(document -> documentVisible(document, account)).orElse(false);
        return account.dataScope() == DataScope.ALL || dimension.ownerType() == OwnerType.CUSTOMER
                && account.dataScope() == DataScope.CUSTOMER_RANGE && visibleCustomer(account, dimension.customerId());
    }

    private boolean entryVisible(StockLedgerEntry entry, AuthUserAccount account) {
        StockDimension dimension = entry.to() == null ? entry.from() : entry.to();
        if (dimension == null) {
            return false;
        }
        return balanceVisible(new StockBalance(dimension, entry.quantity(), entry.weight()), account);
    }

    private boolean visibleCustomer(AuthUserAccount account, Long customerId) {
        return CustomerScopeRules.allowed(BusinessDataAccess.contextOf(account), customerId, "");
    }

    private boolean canReadCompany(AuthUserAccount account) {
        return account.role() == RoleCode.ADMIN || account.role() == RoleCode.WAREHOUSE_KEEPER
                || account.role() == RoleCode.FINANCE_STATISTICIAN || account.role() == RoleCode.GENERAL_MANAGER;
    }

    private OwnerType ownerOf(String scope, Long customerId, AuthUserAccount account) {
        if ("company".equals(normalizeScope(scope))) {
            if (!canReadCompany(account)) {
                throw new PermissionDeniedException("公司自有物料仅仓库、财务、管理员或总经理可以查看");
            }
            return OwnerType.COMPANY;
        }
        assertCustomer(account, customerId);
        return OwnerType.CUSTOMER;
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再操作库存");
        }
    }

    private void requireReceipt(StockDesk.ReceiptInput input, boolean company) {
        if (input == null || input.lines() == null || input.lines().isEmpty()) {
            throw new BusinessException("请至少填写一条物料明细");
        }
        required(input.warehouseCode(), "请填写仓库");
        required(input.requestId(), "库存操作必须带请求编号");
        if (!company) {
            if (input.customerId() == null) {
                throw new BusinessException("请选择客户");
            }
        }
        for (StockDesk.LineInput line : input.lines()) {
            if (line == null) throw new BusinessException("请填写有效物料明细");
            required(line.materialCode(), "请填写物料");
            if (masterData != null) masterData.materialForIntake(line.materialCode().trim());
            required(line.quantityUnit(), "请填写数量单位");
            if (line.quantityUnit().trim().length() > 20 || line.materialCode().trim().length() > 40) {
                throw new BusinessException("物料或数量单位过长");
            }
            requireDraftAmount(line.quantity(), line.weight());
        }
    }

    private void requireIssue(StockDesk.IssueInput input, boolean customer) {
        if (input == null) {
            throw new BusinessException("请填写领用内容");
        }
        required(input.batchCode(), "请选择批次");
        required(input.materialCode(), "请填写物料");
        required(input.warehouseCode(), "请填写仓库");
        required(input.quantityUnit(), "请填写数量单位");
        requirePostedAmount(input.quantity(), input.weight());
        if (customer && input.customerId() == null) {
            throw new BusinessException("请选择客户");
        }
    }

    private void requireReturn(StockDesk.ReturnInput input, boolean customer) {
        if (input == null) {
            throw new BusinessException("请填写退回内容");
        }
        required(input.sourceDocumentCode(), "请选择原领用单");
        required(input.batchCode(), "请选择批次");
        required(input.materialCode(), "请选择物料");
        required(input.warehouseCode(), "请选择仓库");
        requirePostedAmount(input.quantity(), input.weight());
        if (customer && input.customerId() == null) {
            throw new BusinessException("请选择客户");
        }
    }

    private void requireInput(StockDesk.ConfirmInput input) {
        if (input == null || input.documentCode() == null || input.documentCode().isBlank()) {
            throw new BusinessException("请选择收货单");
        }
    }

    private void requireDraftAmount(BigDecimal quantity, BigDecimal weight) {
        if (quantity == null) {
            throw new BusinessException("请填写数量");
        }
        if (quantity.signum() < 0 || weight != null && weight.signum() < 0) {
            throw new BusinessException("数量和重量不能为负");
        }
        checkScale(quantity, "数量");
        checkScale(weight, "重量");
        if (weight == null && quantity.signum() == 0) {
            throw new BusinessException("未称量时数量必须大于零");
        }
        if (weight != null && quantity.signum() == 0 && weight.signum() == 0) {
            throw new BusinessException("数量和重量不能同时为零");
        }
    }

    private void requirePostedAmount(BigDecimal quantity, BigDecimal weight) {
        if (quantity == null || weight == null) {
            throw new BusinessException("入账时数量和重量都要明确，未称量请先保存待核");
        }
        requireDraftAmount(quantity, weight);
        if (quantity.signum() <= 0 || weight.signum() <= 0) {
            throw new BusinessException("正式入账数量和实称重量必须大于零，未称量请先保存待核");
        }
    }

    private void checkScale(BigDecimal value, String label) {
        if (value == null) {
            return;
        }
        if (value.stripTrailingZeros().scale() > 3 || value.abs().compareTo(new BigDecimal("999999999999999.999")) > 0) {
            throw new BusinessException(label + "最多保留三位小数且不得超过数据库范围");
        }
    }

    private StockDocumentLine lineOf(StockDesk.ReturnInput input, String ledgerCode) {
        String unit = unitOfBatch(input.customerId() == null ? OwnerType.COMPANY : OwnerType.CUSTOMER, input.customerId(), input.batchCode(), input.materialCode());
        return lineOf(input.materialCode(), unit, input.quantity(), input.weight(), input.batchCode(), "", input.sourceLineNo(), ledgerCode);
    }

    private StockDocumentLine lineOf(String material, String unit, BigDecimal quantity, BigDecimal weight, String batch, String external, Integer sourceLine, String ledger) {
        return new StockDocumentLine(1, material.trim(), text(unit), quantity, weight, text(batch), text(external), sourceLine, true, quantity, weight, text(ledger));
    }

    private String ledgerType(String kind) {
        return StockDocumentKinds.COMPANY_RECEIPT.equals(kind) ? "company_receipt" : "prep_receipt";
    }

    private String normalizeScope(String scope) {
        String value = text(scope);
        if (!Set.of("company", "prep", "processing").contains(value)) {
            throw new BusinessException("请选择公司自有、客户寄存或加工库存");
        }
        return value;
    }

    private LocalDate parseDate(String value, String message) {
        String day = text(value);
        if (day.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(day);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(message);
        }
    }

    private String required(String value, String message) {
        String normalized = text(value);
        if (normalized.isEmpty()) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value) {
        String normalized = text(value);
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean positive(StockBalance balance) {
        return balance.quantity().signum() > 0 || balance.weight().signum() > 0;
    }

    private static boolean positive(String quantity) {
        return quantity != null && !quantity.isBlank() && new BigDecimal(quantity).signum() > 0;
    }

    private static BigDecimal sum(StockDocument document, boolean quantity) {
        return document.lines().stream().map(line -> quantity ? nz(line.quantity()) : nz(line.weight())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumWeight(StockDocument document) {
        boolean missing = document.lines().stream().anyMatch(line -> line.weight() == null);
        return missing ? null : sum(document, false);
    }

    private static String weightText(BigDecimal weight) {
        return weight == null ? "待称量" : plain(weight);
    }

    private static String customerLabel(StockDocument document) {
        if (document.customerName() != null && !document.customerName().isBlank()) {
            return document.customerName();
        }
        return document.customerId() == null ? "客户未登记" : "客户#" + document.customerId();
    }

    private static String ownerLabel(StockDimension dimension, MotherOrderRecord mother) {
        if (dimension.ownerType() == OwnerType.COMPANY) {
            return "公司自有";
        }
        if (mother != null && mother.customerName() != null && !mother.customerName().isBlank()) {
            return mother.customerName();
        }
        return dimension.customerId() == null ? "客户未登记" : "客户#" + dimension.customerId();
    }

    private static String groupLabel(Accumulator group) {
        if (group.frozenQty.signum() > 0 && group.availableQty.signum() == 0 && group.issuedQty.signum() == 0) {
            return "冻结";
        }
        if (group.issuedQty.signum() > 0 && group.availableQty.signum() == 0) {
            return "已领用未退回";
        }
        if (group.issuedQty.signum() > 0) {
            return "部分领用";
        }
        return "在库";
    }

    private static String statusLabel(StockStatus status) {
        return switch (status) {
            case RAW_WAITING -> "待加工";
            case WIP -> "在制";
            case SEMI_FINISHED -> "待质检";
            case FINISHED -> "成品";
            case FROZEN -> "冻结";
            case REWORK -> "返工";
            case SCRAP -> "报废";
            case SHIPPED -> "已出库";
        };
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case StockDocumentKinds.COMPANY_RECEIPT, "company_receipt" -> "公司料入库";
            case StockDocumentKinds.COMPANY_ISSUE, "company_issue" -> "公司料领用";
            case StockDocumentKinds.COMPANY_RETURN, "company_return" -> "公司料退回";
            case StockDocumentKinds.PREP_RECEIPT, "prep_receipt" -> "寄存收货";
            case StockDocumentKinds.PREP_ISSUE, "prep_issue" -> "备料领用";
            case StockDocumentKinds.PREP_RETURN, "prep_return" -> "退回备料";
            case StockDocumentKinds.PREP_CUSTOMER_RETURN, "prep_customer_return" -> "退还客户";
            case StockDocumentKinds.FREEZE, "stock_freeze" -> "冻结";
            case StockDocumentKinds.UNFREEZE, "stock_unfreeze" -> "解除冻结";
            case StockDocumentKinds.CORRECTION, "stock_correction" -> "冲正";
            case "inbound_confirm" -> "收货入库";
            case "inbound_reversal" -> "收货冲正";
            default -> kind;
        };
    }

    /** 请求快照包括所有字段，避免遗漏仓库、客户或清点数量后错误回放。 */
    private String payload(Object input) {
        try {
            return PAYLOADS.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("库存请求内容无法保存");
        }
    }

    private String receiptPayload(StockDesk.ReceiptInput input) { return payload(input); }
    private String confirmPayload(StockDesk.ConfirmInput input) { return payload(input); }
    private String issuePayload(StockDesk.IssueInput input) { return payload(input); }
    private String returnPayload(StockDesk.ReturnInput input) { return payload(input); }
    private String customerReturnPayload(StockDesk.CustomerReturnInput input) { return payload(input); }
    private String openPayload(StockDesk.OpenOrderInput input) { return payload(input); }
    private String holdPayload(StockDesk.HoldInput input) { return payload(input); }
    private String correctionPayload(StockDesk.CorrectionInput input) { return payload(input); }
    @FunctionalInterface
    private interface BusinessWork {
        String run(WorkSession session);
    }

    private static final class Accumulator {
        private final StockDimension dimension;
        private BigDecimal availableQty = BigDecimal.ZERO;
        private BigDecimal availableWeight = BigDecimal.ZERO;
        private BigDecimal issuedQty = BigDecimal.ZERO;
        private BigDecimal issuedWeight = BigDecimal.ZERO;
        private BigDecimal frozenQty = BigDecimal.ZERO;
        private BigDecimal frozenWeight = BigDecimal.ZERO;
        private BigDecimal bookQty = BigDecimal.ZERO;
        private BigDecimal bookWeight = BigDecimal.ZERO;

        private Accumulator(StockDimension dimension) {
            this.dimension = dimension;
        }

        private void add(StockStatus status, BigDecimal quantity, BigDecimal weight) {
            bookQty = bookQty.add(nz(quantity));
            bookWeight = bookWeight.add(nz(weight));
            if (status == StockStatus.RAW_WAITING) {
                availableQty = availableQty.add(nz(quantity));
                availableWeight = availableWeight.add(nz(weight));
            } else if (status == StockStatus.WIP) {
                issuedQty = issuedQty.add(nz(quantity));
                issuedWeight = issuedWeight.add(nz(weight));
            } else if (status == StockStatus.FROZEN) {
                frozenQty = frozenQty.add(nz(quantity));
                frozenWeight = frozenWeight.add(nz(weight));
            }
        }
    }
}
