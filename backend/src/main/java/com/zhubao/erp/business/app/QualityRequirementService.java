package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.FreezeQualityStockCommand;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessingRequirementClauses;
import com.zhubao.erp.business.domain.QualityCheckCommand;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityCheckLineCommand;
import com.zhubao.erp.business.domain.QualityCheckResult;
import com.zhubao.erp.business.domain.QualityCheckView;
import com.zhubao.erp.business.domain.QualityFreezeResult;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.zhubao.erp.business.domain.ReleaseQualityStockCommand;
import com.zhubao.erp.business.domain.QualityReleaseResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Objects;

/**
 * 质检对照加工要求。质检员取当前登录账号，不合格才调用现有冻结命令。
 */
@Service
public class QualityRequirementService {

    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String WRONG_STICKER = "请扫描子单贴纸，或不拆母单的贴纸";
    private static final String LOGIN_REQUIRED = "请先登录后再做质检对照";
    private static final String PROCESS_BLOCK = "最后一道工序还没完成，不能提交质检";
    private static final String STOCK_BLOCK = "库存还不是半成品，不能提交质检";
    private static final String REQUIREMENT_BLOCK = "这张单没有加工要求，不能提交质检";
    private static final String MISSING_VERDICT = "每一条加工要求都要有结果才能提交";
    private static final String MISSING_REASON = "不合格和让步必须写原因";

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final BusinessOperationService businessOperationService;
    private final ScanCodeParser scanCodeParser;
    private final BusinessDataAccess dataAccess;

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, null);
    }

    @Autowired
    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
        this.businessOperationService = businessOperationService;
        this.scanCodeParser = scanCodeParser;
        this.dataAccess = dataAccess;
    }

    public QualityCheckView lookup(String rawCode, AuthUserAccount account) {
        requireAccount(account);
        Ticket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        QualityRequirementCheck existing = documents.findQualityRequirementCheck(ticket.orderCode()).orElse(null);
        if (existing != null) {
            return submittedView(ticket, existing);
        }
        Readiness readiness = readiness(ticket);
        return new QualityCheckView(
                ticket.orderCode(),
                ticket.motherOrderCode(),
                ticket.customerName(),
                ticket.dueDate(),
                ticket.processingRequirement(),
                readiness.ready(),
                readiness.blockReason(),
                account.operator().trim(),
                "",
                "",
                false,
                "",
                blankLines(readiness.clauses())
        );
    }

    public QualityCheckResult submit(QualityCheckCommand command, AuthUserAccount account) {
        requireAccount(account);
        if (command == null || command.code() == null || command.code().isBlank()) {
            throw new BusinessException("请先扫贴纸");
        }
        Ticket ticket = resolve(command.code());
        assertCustomer(ticket, account);
        if (documents.findQualityRequirementCheck(ticket.orderCode()).isPresent()) {
            throw new BusinessException("这张单已经做过质检对照");
        }
        Readiness readiness = readiness(ticket);
        if (!readiness.ready()) {
            throw new BusinessException(readiness.blockReason());
        }
        List<QualityCheckLine> judged = judge(readiness.clauses(), command.lines());
        // 请求里的 inspector 不读取。写入的人只能是当前登录账号的操作人。
        String inspector = account.operator().trim();
        String outcome = outcomeOf(judged);
        boolean failed = QualityVerdicts.FAIL.equals(outcome);
        String qualityCaseCode = "QC-" + ticket.orderCode();
        String ledgerCode = "";
        if (failed) {
            ledgerCode = freeze(ticket, qualityCaseCode, inspector, failSummary(judged)).ledgerCode();
        }
        List<QualityCheckLine> storedLines = List.copyOf(judged);
        documents.saveQualityRequirementCheck(new QualityRequirementCheck(
                qualityCaseCode,
                ticket.orderCode(),
                ticket.motherOrderCode(),
                inspector,
                outcome,
                failed,
                ledgerCode,
                "",
                RECORD_TIME.format(LocalDateTime.now()),
                storedLines
        ));
        return new QualityCheckResult(
                qualityCaseCode,
                ticket.orderCode(),
                ticket.motherOrderCode(),
                inspector,
                outcome,
                failed,
                ledgerCode,
                "",
                storedLines
        );
    }

    public QualityReworkResult openRework(String rawCode, AuthUserAccount account) {
        requireAccount(account);
        Ticket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        QualityRequirementCheck check = documents.findQualityRequirementCheck(ticket.orderCode())
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        if (!QualityVerdicts.FAIL.equals(check.outcome())) {
            throw new BusinessException("合格或让步不能开返工");
        }
        if (!check.reworkOrderCode().isBlank()) {
            throw new BusinessException("已经开过返工");
        }
        StockBalance frozen = balanceOf(frozenDimension(ticket));
        if (frozen.quantity().compareTo(BigDecimal.ZERO) <= 0 || frozen.weight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("冻结库存不存在，不能开返工");
        }
        String inspector = account.operator().trim();
        String reworkOrderCode = "FG-" + ticket.orderCode();
        ReworkDispatchResult dispatched = businessOperationService.dispatchRework(new DispatchReworkCommand(
                reworkOrderCode,
                check.qualityCaseCode(),
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.warehouseCode(),
                frozen.quantity(),
                frozen.weight(),
                "返工" + lastProcess(ticket),
                inspector,
                failSummary(check.lines()),
                null,
                "原单：" + ticket.motherOrderCode() + "/" + ticket.orderCode()
        ));
        documents.attachQualityRework(ticket.orderCode(), dispatched.reworkOrderCode());
        return new QualityReworkResult(
                dispatched.reworkOrderCode(),
                dispatched.qualityCaseCode(),
                ticket.orderCode(),
                ticket.motherOrderCode(),
                dispatched.ledgerCode(),
                dispatched.fromStockStatus().name(),
                dispatched.toStockStatus().name()
        );
    }

    /** 兼容放行必须以末工序交付与已保存的合格或让步质检事实为依据。 */
    public QualityReleaseResult release(ReleaseQualityStockCommand submitted, AuthUserAccount account) {
        requireAccount(account);
        ReleaseQualityStockCommand command = BusinessCommandActors.withActor(submitted, account.operator().trim());
        Ticket ticket = resolve(command.workOrderCode());
        assertCustomer(ticket, account);
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        if (!Objects.equals(ticket.motherOrderCode(), command.motherOrderCode())
                || !Objects.equals(ticket.customerId(), command.customerId())
                || !Objects.equals(ticket.ownerCustomerId(), ownerCustomerId)
                || !Objects.equals(ticket.materialCode(), command.materialCode())
                || !Objects.equals(ticket.warehouseCode(), command.fromWarehouseCode())
                || !lastProcess(ticket).equals(command.process())) {
            throw new BusinessException("放行的客户、母单、物料、来源仓库和工序必须与正式工单一致");
        }
        if (!lastProcessSubmitted(ticket)) {
            throw new BusinessException(PROCESS_BLOCK);
        }
        QualityRequirementCheck check = documents.findQualityRequirementCheck(ticket.orderCode())
                .orElseThrow(() -> new BusinessException("请先完成质检对照，不能直接放行"));
        if (!Objects.equals(check.motherOrderCode(), ticket.motherOrderCode())
                || !Objects.equals(check.qualityCaseCode(), command.qualityCaseCode())
                || check.frozen()
                || (!QualityVerdicts.PASS.equals(check.outcome()) && !QualityVerdicts.CONCESSION.equals(check.outcome()))) {
            throw new BusinessException("只有真实合格或让步的质检结果才能放行");
        }
        // 放行说明原样保留；能否放行由已经保存的逐项质检结论决定。
        return businessOperationService.releaseQualityStock(command);
    }

    private void assertCustomer(Ticket ticket, AuthUserAccount account) {
        if (dataAccess != null) {
            dataAccess.assertCustomer(BusinessDataAccess.contextOf(account), ticket.customerId(), ticket.customerName());
        }
    }

    private QualityFreezeResult freeze(Ticket ticket, String qualityCaseCode, String inspector, String defect) {
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket));
        return businessOperationService.freezeQualityStock(new FreezeQualityStockCommand(
                qualityCaseCode,
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.warehouseCode(),
                semiFinished.quantity(),
                semiFinished.weight(),
                lastProcess(ticket),
                inspector,
                defect,
                null,
                "原单：" + ticket.motherOrderCode() + "/" + ticket.orderCode()
        ));
    }

    private List<QualityCheckLine> judge(List<String> clauses, List<QualityCheckLineCommand> submitted) {
        if (clauses.isEmpty()) {
            throw new BusinessException(REQUIREMENT_BLOCK);
        }
        if (submitted == null || submitted.isEmpty()) {
            throw new BusinessException(MISSING_VERDICT);
        }
        Map<Integer, QualityCheckLineCommand> byNo = new LinkedHashMap<>();
        for (QualityCheckLineCommand line : submitted) {
            if (line == null || line.lineNo() == null || byNo.put(line.lineNo(), line) != null) {
                throw new BusinessException(MISSING_VERDICT);
            }
        }
        if (byNo.size() != clauses.size()) {
            throw new BusinessException(MISSING_VERDICT);
        }
        List<QualityCheckLine> judged = new ArrayList<>();
        for (int index = 0; index < clauses.size(); index++) {
            int lineNo = index + 1;
            QualityCheckLineCommand line = byNo.get(lineNo);
            if (line == null) {
                throw new BusinessException(MISSING_VERDICT);
            }
            String verdict = QualityVerdicts.normalize(line.verdict());
            String reason = line.reason() == null ? "" : line.reason().trim();
            if (reason.length() > 500) {
                throw new BusinessException("原因不能超过 500 字");
            }
            if (QualityVerdicts.needsReason(verdict) && reason.isEmpty()) {
                throw new BusinessException(MISSING_REASON);
            }
            judged.add(new QualityCheckLine(lineNo, clauses.get(index), verdict, reason));
        }
        return judged;
    }

    private String outcomeOf(List<QualityCheckLine> lines) {
        boolean failed = false;
        boolean concession = false;
        for (QualityCheckLine line : lines) {
            if (QualityVerdicts.FAIL.equals(line.verdict())) {
                failed = true;
            } else if (QualityVerdicts.CONCESSION.equals(line.verdict())) {
                concession = true;
            }
        }
        if (failed) {
            return QualityVerdicts.FAIL;
        }
        if (concession) {
            return QualityVerdicts.CONCESSION;
        }
        return QualityVerdicts.PASS;
    }

    private String failSummary(List<QualityCheckLine> lines) {
        StringJoiner joiner = new StringJoiner("；");
        for (QualityCheckLine line : lines) {
            if (QualityVerdicts.FAIL.equals(line.verdict())) {
                joiner.add(line.clause() + "：" + line.reason());
            }
        }
        String text = joiner.toString();
        if (text.isBlank()) {
            return "质检不合格";
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private Readiness readiness(Ticket ticket) {
        List<String> clauses = ProcessingRequirementClauses.split(ticket.processingRequirement());
        if (!lastProcessSubmitted(ticket)) {
            return new Readiness(false, PROCESS_BLOCK, clauses);
        }
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket));
        if (semiFinished.quantity().compareTo(BigDecimal.ZERO) <= 0
                || semiFinished.weight().compareTo(BigDecimal.ZERO) <= 0) {
            return new Readiness(false, STOCK_BLOCK, clauses);
        }
        if (clauses.isEmpty()) {
            return new Readiness(false, REQUIREMENT_BLOCK, clauses);
        }
        return new Readiness(true, "", clauses);
    }

    private boolean lastProcessSubmitted(Ticket ticket) {
        return documents.findProcessStationHandoff(ticket.orderCode(), lastProcess(ticket)).isPresent();
    }

    private String lastProcess(Ticket ticket) {
        return ticket.processes().get(ticket.processes().size() - 1);
    }

    private List<QualityCheckLine> blankLines(List<String> clauses) {
        List<QualityCheckLine> lines = new ArrayList<>();
        for (int index = 0; index < clauses.size(); index++) {
            lines.add(new QualityCheckLine(index + 1, clauses.get(index), "", ""));
        }
        return lines;
    }

    private QualityCheckView submittedView(Ticket ticket, QualityRequirementCheck check) {
        return new QualityCheckView(
                ticket.orderCode(),
                ticket.motherOrderCode(),
                ticket.customerName(),
                ticket.dueDate(),
                ticket.processingRequirement(),
                false,
                "",
                check.inspector(),
                check.qualityCaseCode(),
                check.outcome(),
                check.frozen(),
                check.reworkOrderCode(),
                check.lines()
        );
    }

    private Ticket resolve(String rawCode) {
        String text = rawCode == null ? "" : rawCode.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请先扫贴纸");
        }
        if (text.startsWith("JG|")) {
            ScanResolveResponse resolved = scanCodeParser.resolve(text, "quality_check");
            if (resolved.objectType() == ScanObjectType.WORK_ORDER) {
                return childTicket(resolved.code());
            }
            if (resolved.objectType() == ScanObjectType.MOTHER_ORDER) {
                return motherTicket(resolved.code());
            }
            throw new BusinessException(WRONG_STICKER);
        }
        if (documents.findChildWorkOrderByCode(text).isPresent()) {
            return childTicket(text);
        }
        if (documents.findMotherOrderByCode(text).isPresent()) {
            return motherTicket(text);
        }
        throw new BusinessException(WRONG_STICKER);
    }

    private Ticket childTicket(String code) {
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code)
                .orElseThrow(() -> new BusinessException("子单不存在：" + code));
        MotherOrderRecord mother = requireMother(child.motherOrderCode());
        List<String> stored = WorkshopProcesses.split(child.processes());
        if (stored.isEmpty()) {
            throw new BusinessException("这张子单没有工序");
        }
        return ticket(
                child.code(),
                mother.code(),
                child.customerId() == null ? mother.customerId() : child.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                blank(child.customerName()),
                blank(child.processingRequirement()),
                blank(child.dueDate()),
                blank(child.materialCode()),
                blank(child.warehouseCode()),
                WorkshopProcesses.normalize(stored)
        );
    }

    private Ticket motherTicket(String code) {
        MotherOrderRecord mother = requireMother(code);
        MotherSplitState state = documents.findMotherSplitState(mother.code()).orElse(null);
        if (!documents.listChildWorkOrders(mother.code()).isEmpty()
                || (state != null && MotherSplitState.SPLIT.equals(state.decision()))) {
            throw new BusinessException("这张母单已经拆成子单，请扫描子单贴纸");
        }
        if (state == null || !MotherSplitState.KEEP.equals(state.decision())) {
            throw new BusinessException("请先选择不拆，再扫母单贴纸");
        }
        return ticket(
                mother.code(),
                mother.code(),
                mother.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                blank(mother.customerName()),
                blank(mother.processingRequirement()),
                blank(mother.dueDate()),
                blank(mother.materialCode()),
                blank(mother.warehouseCode()),
                WorkshopProcesses.DEFAULT_ORDER
        );
    }

    private Ticket ticket(
            String orderCode,
            String motherOrderCode,
            Long customerId,
            Long ownerCustomerId,
            String customerName,
            String processingRequirement,
            String dueDate,
            String materialCode,
            String warehouseCode,
            List<String> processes
    ) {
        if (customerId == null) {
            throw new BusinessException("这张单没有客户，不能质检");
        }
        if (materialCode.isBlank() || warehouseCode.isBlank()) {
            throw new BusinessException("这张单的物料或仓库不完整，不能质检");
        }
        return new Ticket(
                orderCode,
                motherOrderCode,
                customerId,
                ownerCustomerId == null ? customerId : ownerCustomerId,
                customerName,
                processingRequirement,
                dueDate,
                materialCode,
                warehouseCode,
                processes
        );
    }

    private StockDimension semiFinishedDimension(Ticket ticket) {
        return dimension(ticket, StockStatus.SEMI_FINISHED);
    }

    private StockDimension frozenDimension(Ticket ticket) {
        return dimension(ticket, StockStatus.FROZEN);
    }

    private StockDimension dimension(Ticket ticket, StockStatus status) {
        return new StockDimension(
                OwnerType.CUSTOMER,
                ticket.ownerCustomerId(),
                ticket.customerId(),
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                status
        );
    }

    private StockBalance balanceOf(StockDimension dimension) {
        return stockLedgerService.balances().stream()
                .filter(balance -> dimension.equals(balance.dimension()))
                .findFirst()
                .orElse(new StockBalance(dimension, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private MotherOrderRecord requireMother(String code) {
        return documents.findMotherOrderByCode(code)
                .orElseThrow(() -> new BusinessException("母单不存在：" + code));
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException(LOGIN_REQUIRED);
        }
        if (!List.of(RoleCode.ADMIN, RoleCode.GENERAL_MANAGER, RoleCode.WORKSHOP_LEADER, RoleCode.QUALITY_INSPECTOR).contains(account.role())) {
            throw new PermissionDeniedException("当前账号不是质检岗位");
        }
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private record Readiness(boolean ready, String blockReason, List<String> clauses) {
    }

    private record Ticket(
            String orderCode,
            String motherOrderCode,
            Long customerId,
            Long ownerCustomerId,
            String customerName,
            String processingRequirement,
            String dueDate,
            String materialCode,
            String warehouseCode,
            List<String> processes
    ) {
    }
}
