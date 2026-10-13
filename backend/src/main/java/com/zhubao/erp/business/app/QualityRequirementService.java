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
import com.zhubao.erp.business.domain.QualityFollowCommand;
import com.zhubao.erp.business.domain.QualityRecheckCommand;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityReworkReport;
import com.zhubao.erp.business.domain.QualityReworkResult;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.DispatchReworkCommand;
import com.zhubao.erp.business.domain.ReportReworkCommand;
import com.zhubao.erp.business.domain.ReworkDispatchResult;
import com.zhubao.erp.business.domain.ReworkReportResult;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.label.app.LabelVersionGuard;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanResolveResponse;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
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
import java.util.Optional;
import java.util.StringJoiner;
import java.util.Objects;

/**
 * 质检对照加工要求。质检员取当前登录账号，不合格才调用现有冻结命令。
 * 带请求编号的初检、开返工、返工报工、复检和放行进入业务工作单元。
 * 未带请求编号时，库存和质检事实仍各自提交，不回放。
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
    private final BusinessWorkUnit workUnit;
    private final QualityFactSaveProbe factProbe;
    private final OrderRequirementRepository requirements;
    private LabelVersionGuard labelGuard;

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, null);
    }

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, null);
    }

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, QualityFactSaveProbe.NONE, null);
    }

    @Autowired
    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            OrderRequirementRepository requirements
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, QualityFactSaveProbe.NONE, requirements);
    }

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            QualityFactSaveProbe factProbe
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, factProbe, null);
    }

    public QualityRequirementService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            QualityFactSaveProbe factProbe,
            OrderRequirementRepository requirements
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
        this.businessOperationService = businessOperationService;
        this.scanCodeParser = scanCodeParser;
        this.dataAccess = dataAccess;
        this.workUnit = workUnit;
        this.factProbe = factProbe == null ? QualityFactSaveProbe.NONE : factProbe;
        this.requirements = requirements;
    }

    @Autowired(required = false)
    public void setLabelVersionGuard(LabelVersionGuard labelGuard) {
        this.labelGuard = labelGuard;
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
        return submit(command, account, command == null ? null : command.requestId());
    }

    /**
     * 带请求编号时，初检条款、轮次、适用冻结流水和成功结果同一次提交。
     * 同账号、同请求、同规范化内容回放原结果。未带编号时仍各自提交，不回放。
     * 未装配工作单元时，带编号的调用直接拒绝，不能退回旧路径。
     */
    public QualityCheckResult submit(QualityCheckCommand command, AuthUserAccount account, String requestId) {
        requireAccount(account);
        Ticket ticket = requireTicket(command == null ? null : command.code(), account);
        if (labelGuard != null && command != null) {
            labelGuard.check(command.code(), ticket.motherOrderCode(), ticket.orderCode(), command.labelExceptionReason(), account);
        }
        if (requestId == null || requestId.isBlank()) {
            return postInitial(command, account, ticket, null);
        }
        requireWorkUnit();
        List<QualityCheckLine> judged = previewLines(ticket, command.lines());
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "QUALITY_CHECK",
                targetType(ticket),
                ticket.orderCode(),
                QualityCommandText.payload(ticket.customerId(), ticket.ownerCustomerId(), ticket.motherOrderCode(), judged)
        ), session -> QualityCommandText.writeCheck(postInitial(command, account, ticket, session)));
        return QualityCommandText.readCheck(text);
    }

    public QualityReworkResult openRework(String rawCode, AuthUserAccount account) {
        return openRework(rawCode, account, null);
    }

    /**
     * 带请求编号时，返工关联和冻结转返工的库存同一次提交。
     * 不改写原不合格条款。未带编号时仍各自提交。
     */
    public QualityReworkResult openRework(String rawCode, AuthUserAccount account, String requestId) {
        requireAccount(account);
        Ticket ticket = requireTicket(rawCode, account);
        if (requestId == null || requestId.isBlank()) {
            return postRework(account, ticket, null);
        }
        requireWorkUnit();
        QualityRequirementCheck check = findCheck(ticket.orderCode(), null)
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "QUALITY_REWORK",
                targetType(ticket),
                ticket.orderCode(),
                QualityCommandText.payload(ticket.customerId(), ticket.ownerCustomerId(), ticket.motherOrderCode(), check.qualityCaseCode())
        ), session -> QualityCommandText.writeRework(postRework(account, ticket, session)));
        return QualityCommandText.readRework(text);
    }

    public ReworkReportResult reportRework(QualityFollowCommand command, AuthUserAccount account) {
        requireAccount(account);
        Ticket ticket = requireTicket(command == null ? null : command.code(), account);
        String requestId = command == null ? null : command.requestId();
        if (requestId == null || requestId.isBlank()) {
            return postReport(account, ticket, null);
        }
        requireWorkUnit();
        QualityRequirementCheck check = findCheck(ticket.orderCode(), null)
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "REWORK_REPORT",
                targetType(ticket),
                ticket.orderCode(),
                QualityCommandText.payload(ticket.customerId(), ticket.ownerCustomerId(), ticket.motherOrderCode(), check.reworkOrderCode())
        ), session -> QualityCommandText.writeReport(postReport(account, ticket, session)));
        return QualityCommandText.readReport(text);
    }

    public QualityCheckResult recheck(QualityRecheckCommand command, AuthUserAccount account) {
        requireAccount(account);
        Ticket ticket = requireTicket(command == null ? null : command.code(), account);
        String requestId = command == null ? null : command.requestId();
        if (requestId == null || requestId.isBlank()) {
            return postRecheck(command, account, ticket, null);
        }
        requireWorkUnit();
        List<QualityCheckLine> judged = judge(ProcessingRequirementClauses.split(ticket.processingRequirement()), command.lines());
        QualityReworkReport report = reportOf(findCheck(ticket.orderCode(), null).orElse(null), null);
        String marker = report == null ? "" : report.reworkOrderCode() + "/" + report.ledgerCode();
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "QUALITY_RECHECK",
                targetType(ticket),
                ticket.orderCode(),
                QualityCommandText.payload(ticket.customerId(), ticket.ownerCustomerId(), ticket.motherOrderCode(), judged)
                        + ";rework=" + marker
        ), session -> QualityCommandText.writeCheck(postRecheck(command, account, ticket, session)));
        return QualityCommandText.readCheck(text);
    }

    public QualityReleaseResult releaseRound(QualityFollowCommand command, AuthUserAccount account) {
        requireAccount(account);
        Ticket ticket = requireTicket(command == null ? null : command.code(), account);
        String requestId = command == null ? null : command.requestId();
        if (requestId == null || requestId.isBlank()) {
            return postRelease(account, ticket, null);
        }
        requireWorkUnit();
        QualityRound latest = latestRound(visibleRounds(ticket.orderCode(), findCheck(ticket.orderCode(), null).orElse(null), null));
        String marker = latest == null ? "" : latest.roundCode() + "/" + latest.outcome();
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "QUALITY_RELEASE",
                targetType(ticket),
                ticket.orderCode(),
                QualityCommandText.payload(ticket.customerId(), ticket.ownerCustomerId(), ticket.motherOrderCode(), marker)
        ), session -> QualityCommandText.writeRelease(postRelease(account, ticket, session)));
        return QualityCommandText.readRelease(text);
    }

    /** 桌面放行仍用原命令。能否放行看最新轮次和真实库存，不把原质检的冻结标志改成未冻结。 */
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
        QualityRequirementCheck check = findCheck(ticket.orderCode(), null)
                .orElseThrow(() -> new BusinessException("请先完成质检对照，不能直接放行"));
        QualityRound latest = latestRound(visibleRounds(ticket.orderCode(), check, null));
        String block = releaseBlock(ticket, latest, null);
        if (!block.isEmpty()
                || !Objects.equals(check.motherOrderCode(), ticket.motherOrderCode())
                || !Objects.equals(check.qualityCaseCode(), command.qualityCaseCode())) {
            throw new BusinessException(block.isEmpty() ? "只有最新轮次合格或让步才能放行" : block);
        }
        return businessOperationService.releaseQualityStock(command);
    }

    private QualityCheckResult postInitial(
            QualityCheckCommand command,
            AuthUserAccount account,
            Ticket ticket,
            WorkSession session
    ) {
        if (findCheck(ticket.orderCode(), session).isPresent()) {
            throw new BusinessException("这张单已经做过质检对照");
        }
        Readiness readiness = readiness(ticket, session);
        if (!readiness.ready()) {
            throw new BusinessException(readiness.blockReason());
        }
        List<QualityCheckLine> judged = judge(readiness.clauses(), command.lines());
        String inspector = account.operator().trim();
        String outcome = outcomeOf(judged);
        boolean failed = QualityVerdicts.FAIL.equals(outcome);
        String qualityCaseCode = "QC-" + ticket.orderCode();
        String ledgerCode = "";
        if (failed) {
            ledgerCode = freeze(ticket, qualityCaseCode, inspector, failSummary(judged), session).ledgerCode();
        }
        factProbe.beforeFactSave();
        String now = RECORD_TIME.format(LocalDateTime.now());
        QualityRequirementCheck check = new QualityRequirementCheck(
                qualityCaseCode,
                ticket.orderCode(),
                ticket.motherOrderCode(),
                inspector,
                outcome,
                failed,
                ledgerCode,
                "",
                now,
                List.copyOf(judged)
        );
        saveCheck(check, session);
        QualityRound round = roundOf(ticket, check, 1, QualityRound.INITIAL, "", "", outcome, failed, ledgerCode, inspector, now, judged);
        saveRound(round, session);
        return resultOf(check, round);
    }

    private QualityReworkResult postRework(AuthUserAccount account, Ticket ticket, WorkSession session) {
        QualityRequirementCheck check = findCheck(ticket.orderCode(), session)
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        if (!QualityVerdicts.FAIL.equals(check.outcome())) {
            throw new BusinessException("合格或让步不能开返工");
        }
        if (!check.reworkOrderCode().isBlank()) {
            throw new BusinessException("已经开过返工");
        }
        StockBalance frozen = balanceOf(frozenDimension(ticket), session);
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
        ), session);
        factProbe.beforeFactSave();
        attachRework(ticket.orderCode(), dispatched.reworkOrderCode(), session);
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

    private ReworkReportResult postReport(AuthUserAccount account, Ticket ticket, WorkSession session) {
        QualityRequirementCheck check = findCheck(ticket.orderCode(), session)
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        if (check.reworkOrderCode().isBlank()) {
            throw new BusinessException("请先开返工");
        }
        if (findReport(check.reworkOrderCode(), session).isPresent()) {
            throw new BusinessException("返工已经报过工");
        }
        StockBalance rework = balanceOf(reworkDimension(ticket), session);
        if (rework.quantity().compareTo(BigDecimal.ZERO) <= 0 || rework.weight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("没有可报工的返工库存");
        }
        String inspector = account.operator().trim();
        ReworkReportResult reported = businessOperationService.reportRework(new ReportReworkCommand(
                check.reworkOrderCode(),
                check.qualityCaseCode(),
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.warehouseCode(),
                rework.quantity(),
                rework.weight(),
                "返工" + lastProcess(ticket),
                inspector,
                null,
                "原单：" + ticket.motherOrderCode() + "/" + ticket.orderCode()
        ), session);
        factProbe.beforeFactSave();
        saveReport(new QualityReworkReport(
                reported.reworkOrderCode(),
                reported.qualityCaseCode(),
                ticket.orderCode(),
                ticket.motherOrderCode(),
                reported.ledgerCode(),
                inspector,
                RECORD_TIME.format(LocalDateTime.now())
        ), session);
        return reported;
    }

    private QualityCheckResult postRecheck(
            QualityRecheckCommand command,
            AuthUserAccount account,
            Ticket ticket,
            WorkSession session
    ) {
        QualityRequirementCheck check = findCheck(ticket.orderCode(), session)
                .orElseThrow(() -> new BusinessException("请先提交质检对照"));
        List<QualityRound> rounds = visibleRounds(ticket.orderCode(), check, session);
        QualityReworkReport report = reportOf(check, session);
        String block = recheckBlock(ticket, check, rounds, report, session);
        if (!block.isEmpty()) {
            throw new BusinessException(block);
        }
        Readiness readiness = readiness(ticket, session);
        if (readiness.clauses().isEmpty()) {
            throw new BusinessException(REQUIREMENT_BLOCK);
        }
        List<QualityCheckLine> judged = judge(readiness.clauses(), command.lines());
        String inspector = account.operator().trim();
        String outcome = outcomeOf(judged);
        boolean failed = QualityVerdicts.FAIL.equals(outcome);
        String ledgerCode = "";
        int roundNo = nextRoundNo(rounds, check);
        if (failed) {
            ledgerCode = freeze(ticket, "QR-" + ticket.orderCode() + "-" + roundNo, inspector, failSummary(judged), session).ledgerCode();
        }
        factProbe.beforeFactSave();
        String now = RECORD_TIME.format(LocalDateTime.now());
        QualityRound round = roundOf(
                ticket,
                check,
                roundNo,
                QualityRound.RECHECK,
                check.reworkOrderCode(),
                report.ledgerCode(),
                outcome,
                failed,
                ledgerCode,
                inspector,
                now,
                judged
        );
        saveRound(round, session);
        return resultOf(check, round);
    }

    private QualityReleaseResult postRelease(AuthUserAccount account, Ticket ticket, WorkSession session) {
        QualityRequirementCheck check = findCheck(ticket.orderCode(), session)
                .orElseThrow(() -> new BusinessException("请先完成质检对照，不能直接放行"));
        QualityRound latest = latestRound(visibleRounds(ticket.orderCode(), check, session));
        String block = releaseBlock(ticket, latest, session);
        if (!block.isEmpty()) {
            throw new BusinessException(block);
        }
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket), session);
        String inspector = account.operator().trim();
        QualityReleaseResult released = businessOperationService.releaseQualityStock(new ReleaseQualityStockCommand(
                check.qualityCaseCode(),
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
                latest.outcome(),
                null,
                "原单：" + ticket.motherOrderCode() + "/" + ticket.orderCode()
        ), session);
        factProbe.beforeFactSave();
        return released;
    }

    private void requireWorkUnit() {
        if (workUnit == null) {
            throw new BusinessException("质检回放尚未装配");
        }
    }

    private Ticket requireTicket(String rawCode, AuthUserAccount account) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException("请先扫贴纸");
        }
        Ticket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        return ticket;
    }

    /** 只规范化条款，不看当前库存。回放时库存已经变化，不能在进入工作单元前把原结果挡掉。 */
    private List<QualityCheckLine> previewLines(Ticket ticket, List<QualityCheckLineCommand> lines) {
        return judge(ProcessingRequirementClauses.split(ticket.processingRequirement()), lines);
    }

    private String targetType(Ticket ticket) {
        return ticket.orderCode().equals(ticket.motherOrderCode()) ? "MOTHER_ORDER" : "WORK_ORDER";
    }

    private QualityCheckResult resultOf(QualityRequirementCheck check, QualityRound round) {
        return new QualityCheckResult(
                check.qualityCaseCode(),
                check.orderCode(),
                check.motherOrderCode(),
                round.inspector(),
                round.outcome(),
                round.frozen(),
                round.ledgerCode(),
                check.reworkOrderCode(),
                round.lines(),
                round.roundCode(),
                round.roundNo(),
                round.roundType()
        );
    }

    private QualityRound roundOf(
            Ticket ticket,
            QualityRequirementCheck check,
            int roundNo,
            String roundType,
            String reworkOrderCode,
            String reworkReportLedger,
            String outcome,
            boolean frozen,
            String ledgerCode,
            String inspector,
            String createdAt,
            List<QualityCheckLine> lines
    ) {
        return new QualityRound(
                "QR-" + ticket.orderCode() + "-" + roundNo,
                check.qualityCaseCode(),
                ticket.orderCode(),
                ticket.motherOrderCode(),
                roundNo,
                roundType,
                reworkOrderCode,
                reworkReportLedger,
                outcome,
                frozen,
                ledgerCode,
                inspector,
                createdAt,
                lines
        );
    }

    private int nextRoundNo(List<QualityRound> rounds, QualityRequirementCheck check) {
        int max = 0;
        for (QualityRound round : rounds) {
            if (!round.roundCode().isBlank()) {
                max = Math.max(max, round.roundNo());
            }
        }
        if (check != null && max < 1) {
            max = 1;
        }
        return max + 1;
    }

    private QualityRound latestRound(List<QualityRound> rounds) {
        QualityRound latest = null;
        for (QualityRound round : rounds) {
            if (latest == null || round.roundNo() > latest.roundNo()) {
                latest = round;
            }
        }
        return latest;
    }

    private List<QualityRound> visibleRounds(String orderCode, QualityRequirementCheck check, WorkSession session) {
        List<QualityRound> stored = session == null
                ? documents.listQualityRounds(orderCode)
                : documents.listQualityRounds(orderCode, session);
        if (check == null) {
            return stored;
        }
        boolean hasInitial = stored.stream().anyMatch(round -> round.roundNo() == 1 && !round.roundCode().isBlank());
        if (hasInitial) {
            return stored;
        }
        List<QualityRound> visible = new ArrayList<>();
        visible.add(new QualityRound(
                "",
                check.qualityCaseCode(),
                check.orderCode(),
                check.motherOrderCode(),
                1,
                QualityRound.INITIAL,
                check.reworkOrderCode(),
                "",
                check.outcome(),
                check.frozen(),
                check.ledgerCode(),
                check.inspector(),
                check.createdAt(),
                check.lines()
        ));
        visible.addAll(stored);
        return visible;
    }

    private QualityReworkReport reportOf(QualityRequirementCheck check, WorkSession session) {
        if (check == null || check.reworkOrderCode().isBlank()) {
            return null;
        }
        return findReport(check.reworkOrderCode(), session).orElse(null);
    }

    private String recheckBlock(
            Ticket ticket,
            QualityRequirementCheck check,
            List<QualityRound> rounds,
            QualityReworkReport report,
            WorkSession session
    ) {
        if (check == null) {
            return "请先提交质检对照";
        }
        boolean storedRecheck = rounds.stream()
                .anyMatch(round -> QualityRound.RECHECK.equals(round.roundType()) && !round.roundCode().isBlank());
        if (storedRecheck) {
            return "这一轮复检已经提交，不能覆盖";
        }
        QualityRound latest = latestRound(rounds);
        if (latest == null || !QualityVerdicts.FAIL.equals(latest.outcome())) {
            return "当前结论不是不合格，不能复检";
        }
        if (check.reworkOrderCode().isBlank()) {
            return "请先开返工";
        }
        if (report == null || !check.reworkOrderCode().equals(report.reworkOrderCode()) || report.ledgerCode().isBlank()) {
            return "返工还没报工，不能复检";
        }
        if (frozenLeft(ticket, session)) {
            return "还有未处理的冻结，不能复检";
        }
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket), session);
        if (semiFinished.quantity().compareTo(BigDecimal.ZERO) <= 0
                || semiFinished.weight().compareTo(BigDecimal.ZERO) <= 0) {
            return "库存还不是半成品，不能提交复检";
        }
        return "";
    }

    private String releaseBlock(Ticket ticket, QualityRound latest, WorkSession session) {
        if (latest == null) {
            return "请先完成质检对照，不能直接放行";
        }
        if (latest.frozen()
                || (!QualityVerdicts.PASS.equals(latest.outcome()) && !QualityVerdicts.CONCESSION.equals(latest.outcome()))) {
            return "只有最新轮次合格或让步才能放行";
        }
        if (!lastProcessSubmitted(ticket)) {
            return PROCESS_BLOCK;
        }
        if (frozenLeft(ticket, session)) {
            return "还有未处理的冻结，不能放行";
        }
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket), session);
        if (semiFinished.quantity().compareTo(BigDecimal.ZERO) <= 0
                || semiFinished.weight().compareTo(BigDecimal.ZERO) <= 0) {
            return STOCK_BLOCK;
        }
        return "";
    }

    private boolean frozenLeft(Ticket ticket, WorkSession session) {
        StockBalance frozen = balanceOf(frozenDimension(ticket), session);
        return frozen.quantity().compareTo(BigDecimal.ZERO) > 0 || frozen.weight().compareTo(BigDecimal.ZERO) > 0;
    }

    private Optional<QualityRequirementCheck> findCheck(String orderCode, WorkSession session) {
        if (session == null) {
            return documents.findQualityRequirementCheck(orderCode);
        }
        return documents.findQualityRequirementCheck(orderCode, session);
    }

    private Optional<QualityReworkReport> findReport(String reworkOrderCode, WorkSession session) {
        if (session == null) {
            return documents.findQualityReworkReport(reworkOrderCode);
        }
        return documents.findQualityReworkReport(reworkOrderCode, session);
    }

    private void saveCheck(QualityRequirementCheck check, WorkSession session) {
        if (session == null) {
            documents.saveQualityRequirementCheck(check);
            return;
        }
        documents.saveQualityRequirementCheck(check, session);
    }

    private void saveRound(QualityRound round, WorkSession session) {
        if (session == null) {
            documents.saveQualityRound(round);
            return;
        }
        documents.saveQualityRound(round, session);
    }

    private void saveReport(QualityReworkReport report, WorkSession session) {
        if (session == null) {
            documents.saveQualityReworkReport(report);
            return;
        }
        documents.saveQualityReworkReport(report, session);
    }

    private void attachRework(String orderCode, String reworkOrderCode, WorkSession session) {
        if (session == null) {
            documents.attachQualityRework(orderCode, reworkOrderCode);
            return;
        }
        documents.attachQualityRework(orderCode, reworkOrderCode, session);
    }

    private void assertCustomer(Ticket ticket, AuthUserAccount account) {
        if (dataAccess != null) {
            dataAccess.assertCustomer(BusinessDataAccess.contextOf(account), ticket.customerId(), ticket.customerName());
        }
    }

    private QualityFreezeResult freeze(Ticket ticket, String qualityCaseCode, String inspector, String defect, WorkSession session) {
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket), session);
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
        ), session);
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
        return readiness(ticket, null);
    }

    private Readiness readiness(Ticket ticket, WorkSession session) {
        List<String> clauses = ProcessingRequirementClauses.split(ticket.processingRequirement());
        if (!lastProcessSubmitted(ticket)) {
            return new Readiness(false, PROCESS_BLOCK, clauses);
        }
        StockBalance semiFinished = balanceOf(semiFinishedDimension(ticket), session);
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
        List<QualityRound> rounds = visibleRounds(ticket.orderCode(), check, null);
        QualityRound latest = latestRound(rounds);
        QualityReworkReport report = reportOf(check, null);
        String recheckBlock = recheckBlock(ticket, check, rounds, report, null);
        String releaseBlock = releaseBlock(ticket, latest, null);
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
                check.lines(),
                latest == null ? "" : latest.roundCode(),
                latest == null ? 0 : latest.roundNo(),
                latest == null ? "" : latest.roundType(),
                report != null,
                recheckBlock.isEmpty(),
                recheckBlock,
                releaseBlock.isEmpty(),
                releaseBlock,
                rounds
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
        var version = OrderRequirementLookup.current(requirements, motherOrderCode, orderCode).orElse(null);
        processingRequirement = OrderRequirementLookup.requirement(version, processingRequirement);
        dueDate = OrderRequirementLookup.dueDate(version, dueDate);
        processes = OrderRequirementLookup.route(version, processes);
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

    private StockDimension reworkDimension(Ticket ticket) {
        return dimension(ticket, StockStatus.REWORK);
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
        return balanceOf(dimension, null);
    }

    private StockBalance balanceOf(StockDimension dimension, WorkSession session) {
        if (session != null) {
            return stockLedgerService.balance(dimension, session);
        }
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
