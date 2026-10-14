package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.ProcessProgressChildOption;
import com.zhubao.erp.business.domain.ProcessProgressTarget;
import com.zhubao.erp.business.domain.ProcessStationCard;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.ProcessStationReportResult;
import com.zhubao.erp.business.domain.ProcessStationView;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 三个工序岗位扫码。只展示账号绑定的工序，并按单上的顺序交付。
 * 中间工序写入台账但留在在制；最后一道再把在制转入半成品。
 * 带请求编号的交付进入业务工作单元：不拆母单首次转在制、工序流水、交付事实和成功结果同一次提交。
 * 未带请求编号的交付和通用报工仍各自提交。带编号的通用报工与现场交付进入同一次回放。
 */
@Service
public class ProcessStationService {

    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String STATION_DOCUMENT = "process_station_report";
    private static final String WRONG_STICKER = "请扫描子单贴纸，或不拆母单的贴纸";

    private final BusinessDocumentRepository documents;
    private final StockLedgerService stockLedgerService;
    private final BusinessOperationService businessOperationService;
    private final ScanCodeParser scanCodeParser;
    private final BusinessDataAccess dataAccess;
    private final BusinessWorkUnit workUnit;
    private final HandoffSaveProbe handoffSaveProbe;
    private final OrderRequirementRepository requirements;
    private LabelVersionGuard labelGuard;

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, null);
    }

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, null);
    }

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, HandoffSaveProbe.NONE, null);
    }

    @Autowired
    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            OrderRequirementRepository requirements
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, HandoffSaveProbe.NONE, requirements);
    }

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            HandoffSaveProbe handoffSaveProbe
    ) {
        this(documents, stockLedgerService, businessOperationService, scanCodeParser, dataAccess, workUnit, handoffSaveProbe, null);
    }

    public ProcessStationService(
            BusinessDocumentRepository documents,
            StockLedgerService stockLedgerService,
            BusinessOperationService businessOperationService,
            ScanCodeParser scanCodeParser,
            BusinessDataAccess dataAccess,
            BusinessWorkUnit workUnit,
            HandoffSaveProbe handoffSaveProbe,
            OrderRequirementRepository requirements
    ) {
        this.documents = documents;
        this.stockLedgerService = stockLedgerService;
        this.businessOperationService = businessOperationService;
        this.scanCodeParser = scanCodeParser;
        this.dataAccess = dataAccess;
        this.workUnit = workUnit;
        this.handoffSaveProbe = handoffSaveProbe == null ? HandoffSaveProbe.NONE : handoffSaveProbe;
        this.requirements = requirements;
    }

    @Autowired(required = false)
    public void setLabelVersionGuard(LabelVersionGuard labelGuard) {
        this.labelGuard = labelGuard;
    }

    /**
     * 解析进度报工目标。已拆母单返回子单列表；当前工序取第一道尚未正式交付的工序。
     */
    public ProcessProgressTarget openProgress(String rawCode, AuthUserAccount account) {
        requireAccount(account);
        String code = progressCode(rawCode);
        if (documents.findMotherOrderByCode(code).isPresent() && !documents.listChildWorkOrders(code).isEmpty()) {
            MotherOrderRecord mother = requireMother(code);
            assertMotherCustomer(mother, account);
            List<ProcessProgressChildOption> children = documents.listChildWorkOrders(code).stream()
                    .map(child -> new ProcessProgressChildOption(child.code(), blank(child.customerName()), blank(child.processes())))
                    .toList();
            var version = OrderRequirementLookup.current(requirements, mother.code(), "").orElse(null);
            return new ProcessProgressTarget(true, "", mother.code(), blank(mother.customerName()),
                    OrderRequirementLookup.requirement(version, blank(mother.processingRequirement())),
                    OrderRequirementLookup.dueDate(version, blank(mother.dueDate())), "", false, 0, 0, children);
        }
        StationTicket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        String current = unfinishedProcess(ticket);
        boolean delivered = current == null;
        String process = delivered ? ticket.processes().get(ticket.processes().size() - 1) : current;
        if (!boundProcesses(account).contains(process)) {
            throw new BusinessException("当前账号不能做这道工序");
        }
        return new ProcessProgressTarget(false, ticket.orderCode(), ticket.motherOrderCode(), ticket.customerName(),
                ticket.processingRequirement(), ticket.dueDate(), process, delivered, 0, 0, List.of());
    }

    public ProcessStationView lookup(String rawCode, AuthUserAccount account) {
        requireAccount(account);
        StationTicket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        List<String> visible = visibleProcesses(ticket, account);
        if (visible.isEmpty()) {
            return new ProcessStationView(
                    ticket.orderCode(),
                    ticket.customerName(),
                    ticket.dueDate(),
                    ticket.processingRequirement(),
                    List.of(),
                    "当前账号未绑定这张单上的工序，不能报工"
            );
        }
        List<ProcessStationCard> stations = new ArrayList<>();
        for (String process : visible) {
            stations.add(card(ticket, process));
        }
        return new ProcessStationView(
                ticket.orderCode(),
                ticket.customerName(),
                ticket.dueDate(),
                ticket.processingRequirement(),
                stations,
                ""
        );
    }

    public ProcessStationReportResult report(String rawCode, String requestedProcess, AuthUserAccount account) {
        return report(rawCode, requestedProcess, account, null);
    }

    /**
     * 带请求编号时，不拆母单首次转在制、工序流水、交付事实和成功结果同一次提交。
     * 同账号、同请求、同规范化内容回放原结果。未带编号时仍各自提交，不回放。
     * 未装配工作单元时，带编号的调用直接拒绝，不能退回旧路径。
     */
    public ProcessStationReportResult report(String rawCode, String requestedProcess, AuthUserAccount account, String requestId) {
        return report(rawCode, requestedProcess, account, requestId, null);
    }

    public ProcessStationReportResult report(
            String rawCode,
            String requestedProcess,
            AuthUserAccount account,
            String requestId,
            String labelExceptionReason
    ) {
        requireAccount(account);
        if (requestId == null || requestId.isBlank()) {
            synchronized (this) {
        StationTicket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        assertLabel(rawCode, ticket, labelExceptionReason, account);
        return stationResult(postStation(defaultCommand(ticket, requestedProcess, account.operator()), account, null));
            }
        }
        if (workUnit == null) {
            throw new BusinessException("工序交付回放尚未装配");
        }
        StationTicket ticket = resolve(rawCode);
        assertCustomer(ticket, account);
        assertLabel(rawCode, ticket, labelExceptionReason, account);
        String process = normalizeRequested(requestedProcess);
        String operator = account.operator().trim();
        String targetType = ticket.keptMother() ? "MOTHER_ORDER" : "WORK_ORDER";
        String text = workUnit.execute(new CommandCall(
                requestId,
                operator,
                "PROCESS_HANDOFF",
                targetType,
                ticket.orderCode(),
                ProcessHandoffResultText.payload(
                        ticket.customerId(),
                        ticket.ownerCustomerId(),
                        ticket.motherOrderCode(),
                        process,
                        ticket.quantity(),
                        ticket.weight()
                )
        ), session -> ProcessHandoffResultText.write(stationResult(
                postStation(defaultCommand(ticket, process, operator), account, session)
        )));
        return ProcessHandoffResultText.read(text);
    }

    /**
     * 通用报工沿用现场整单交付规则，并完整保存请求中的报工登记字段。
     * 未带请求编号时库存流水和交付事实各自提交，不回放。
     */
    public ProductionReportResult reportProduction(ReportProductionCommand submittedCommand, AuthUserAccount account) {
        return reportProduction(submittedCommand, account, null);
    }

    /**
     * 带请求编号的通用报工与现场交付共用工作单元。
     * 同账号、同请求、同内容回放原结果；未装配工作单元时拒绝，不能退回旧路径。
     */
    public ProductionReportResult reportProduction(
            ReportProductionCommand submittedCommand,
            AuthUserAccount account,
            String requestId
    ) {
        requireAccount(account);
        if (requestId == null || requestId.isBlank()) {
            synchronized (this) {
                return postStation(submittedCommand, account, null);
            }
        }
        if (workUnit == null) {
            throw new BusinessException("工序交付回放尚未装配");
        }
        ReportProductionCommand command = BusinessCommandActors.withActor(submittedCommand, account.operator().trim());
        String text = workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "PROCESS_HANDOFF",
                "WORK_ORDER",
                command.workOrderCode().trim(),
                ProductionReportResultText.payload(command)
        ), session -> ProductionReportResultText.write(postStation(command, account, session)));
        return ProductionReportResultText.read(text);
    }

    private ProductionReportResult postStation(ReportProductionCommand submittedCommand, AuthUserAccount account, WorkSession session) {
        requireAccount(account);
        ReportProductionCommand command = BusinessCommandActors.withActor(submittedCommand, account.operator().trim());
        StationTicket ticket = resolve(command.workOrderCode());
        assertCustomer(ticket, account);
        assertFormalCommand(ticket, command);
        String process = normalizeRequested(command.process());
        if (!ticket.processes().contains(process)) {
            throw new BusinessException("这张单没有「" + process + "」这道工序");
        }
        if (!boundProcesses(account).contains(process)) {
            throw new BusinessException("当前账号不能做这道工序");
        }
        if (submitted(ticket.orderCode(), process, session)) {
            throw new BusinessException("这道工序已经交过");
        }
        String previous = previousProcess(ticket.processes(), process);
        if (!previous.isBlank() && !submitted(ticket.orderCode(), previous, session)) {
            throw new BusinessException("上一道「" + previous + "」还没交，不能交「" + process + "」");
        }
        String operator = account.operator().trim();
        ensureWipForKeptMother(ticket, operator, session);
        boolean last = process.equals(ticket.routeTail());
        StockDimension from = wipDimension(ticket);
        StockDimension to = last ? new StockDimension(
                from.ownerType(), from.ownerCustomerId(), from.customerId(), from.motherOrderCode(),
                from.workOrderCode(), from.materialCode(), command.toWarehouseCode(), StockStatus.SEMI_FINISHED
        ) : from;
        StockTransferCommand transfer = new StockTransferCommand(
                last ? "production_report" : STATION_DOCUMENT,
                ticket.orderCode(), from, to, command.quantity(), command.weight(), productionRemark(command)
        );
        StockLedgerEntry entry = session == null
                ? stockLedgerService.transfer(transfer)
                : stockLedgerService.transfer(transfer, session);
        handoffSaveProbe.beforeHandoffSave();
        ProcessStationHandoff handoff = new ProcessStationHandoff(
                ticket.orderCode(),
                process,
                operator,
                entry.code(),
                RECORD_TIME.format(LocalDateTime.now())
        );
        if (session == null) {
            documents.saveProcessStationHandoff(handoff);
        } else {
            documents.saveProcessStationHandoff(handoff, session);
        }
        return new ProductionReportResult(
                ticket.orderCode(), entry.code(), from.warehouseCode(), to.warehouseCode(),
                from.stockStatus(), to.stockStatus(), entry.quantity(), entry.weight(), process, operator, command.handoffCode()
        );
    }

    private ProcessStationReportResult stationResult(ProductionReportResult result) {
        return new ProcessStationReportResult(
                result.workOrderCode(),
                result.process(),
                result.ledgerCode(),
                result.toStockStatus() == StockStatus.SEMI_FINISHED,
                result.toStockStatus().name()
        );
    }

    private void ensureWipForKeptMother(StationTicket ticket, String operator, WorkSession session) {
        if (!ticket.keptMother()) {
            return;
        }
        StockDimension wip = wipDimension(ticket);
        StockBalance balance = session == null ? balanceOf(wip) : stockLedgerService.balance(wip, session);
        if (balance.quantity().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        StockTransferCommand transfer = new StockTransferCommand(
                "work_order_split",
                ticket.orderCode(),
                rawDimension(ticket),
                wip,
                ticket.quantity(),
                ticket.weight(),
                "不拆母单转入在制；操作人：" + operator
        );
        if (session == null) {
            stockLedgerService.transfer(transfer);
        } else {
            stockLedgerService.transfer(transfer, session);
        }
    }

    private ReportProductionCommand defaultCommand(StationTicket ticket, String process, String operator) {
        return new ReportProductionCommand(
                ticket.motherOrderCode(),
                ticket.orderCode(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.warehouseCode(),
                ticket.quantity(),
                ticket.quantityUnit(),
                ticket.weight(),
                LocalDate.now().toString(),
                process,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                operator,
                null,
                null,
                null
        );
    }

    private void assertFormalCommand(StationTicket ticket, ReportProductionCommand command) {
        Long ownerCustomerId = command.ownerCustomerId() == null ? command.customerId() : command.ownerCustomerId();
        if (!Objects.equals(ticket.motherOrderCode(), command.motherOrderCode())
                || !Objects.equals(ticket.customerId(), command.customerId())
                || !Objects.equals(ticket.ownerCustomerId(), ownerCustomerId)
                || !Objects.equals(ticket.materialCode(), command.materialCode())
                || !Objects.equals(ticket.warehouseCode(), command.fromWarehouseCode())
                || !Objects.equals(ticket.warehouseCode(), command.toWarehouseCode())) {
            throw new BusinessException("报工的客户、母单、物料和仓库必须与正式工单一致");
        }
        if (command.quantity() == null || command.weight() == null
                || ticket.quantity().compareTo(command.quantity()) != 0
                || ticket.weight().compareTo(command.weight()) != 0) {
            throw new BusinessException("工序交付须提交整单数量和重量，部分报工不能标记工序完成");
        }
        if (!blank(command.quantityUnit()).isEmpty() && !ticket.quantityUnit().equals(command.quantityUnit().trim())) {
            throw new BusinessException("报工数量单位必须与正式工单一致");
        }
        if (!blank(command.completionStatus()).isEmpty()
                && !List.of("完成", "已完成", "全部完成").contains(command.completionStatus().trim())) {
            throw new BusinessException("只有整单已完成才能交付工序");
        }
        if (!blank(command.handoffAuditStatus()).isEmpty()
                && !List.of("已审核", "审核通过", "已交单", "通过").contains(command.handoffAuditStatus().trim())) {
            throw new BusinessException("交单审核未通过，不能交付工序");
        }
    }

    private String productionRemark(ReportProductionCommand command) {
        StringBuilder remark = new StringBuilder("生产报工；工序：").append(command.process())
                .append("；操作人：").append(command.operator());
        appendField(remark, "工序日期", command.workDate());
        appendField(remark, "工序类型", command.processCategory());
        appendField(remark, "品类", command.productCategory());
        appendField(remark, "数量单位", command.quantityUnit());
        appendField(remark, "机器编号", command.machineNo());
        appendField(remark, "操机签字", command.operatorSignatureName());
        appendField(remark, "工作项目", command.workItemName());
        appendField(remark, "开始时间", command.startedAt());
        appendField(remark, "完成时间", command.finishedAt());
        appendField(remark, "完成情况", command.completionStatus());
        appendField(remark, "交单审核", command.handoffAuditStatus());
        appendField(remark, "来源登记表", command.sourceRegisterType());
        appendField(remark, "原表行号", command.sourceRegisterLineNo() == null ? null : command.sourceRegisterLineNo().toString());
        appendField(remark, "纸质序号", command.sourceRegisterSequence());
        appendField(remark, "交接", command.handoffCode());
        appendField(remark, "备注", command.remark());
        return remark.toString();
    }

    private void appendField(StringBuilder remark, String label, String value) {
        if (!blank(value).isEmpty()) {
            remark.append("；").append(label).append("：").append(value);
        }
    }

    private void assertCustomer(StationTicket ticket, AuthUserAccount account) {
        if (dataAccess != null) {
            dataAccess.assertCustomer(BusinessDataAccess.contextOf(account), ticket.customerId(), ticket.customerName());
        }
    }

    private ProcessStationCard card(StationTicket ticket, String process) {
        String previous = previousProcess(ticket.processes(), process);
        boolean previousSubmitted = previous.isBlank() || submitted(ticket.orderCode(), previous);
        return new ProcessStationCard(
                process,
                ticket.processingRequirement(),
                ticket.dueDate(),
                previous,
                previousSubmitted,
                submitted(ticket.orderCode(), process)
        );
    }

    private List<String> visibleProcesses(StationTicket ticket, AuthUserAccount account) {
        List<String> allowed = boundProcesses(account);
        List<String> visible = new ArrayList<>();
        for (String process : ticket.processes()) {
            if (allowed.contains(process)) {
                visible.add(process);
            }
        }
        return visible;
    }

    private List<String> boundProcesses(AuthUserAccount account) {
        return WorkshopProcesses.bind(account.allowedProcesses());
    }

    private String normalizeRequested(String requestedProcess) {
        if (requestedProcess == null || requestedProcess.isBlank()) {
            throw new BusinessException("请选择要交付的工序");
        }
        return WorkshopProcesses.normalize(List.of(requestedProcess.trim())).get(0);
    }

    private String previousProcess(List<String> processes, String process) {
        int index = processes.indexOf(process);
        if (index <= 0) {
            return "";
        }
        return processes.get(index - 1);
    }

    private boolean submitted(String orderCode, String process) {
        return submitted(orderCode, process, null);
    }

    private boolean submitted(String orderCode, String process, WorkSession session) {
        if (session == null) {
            return documents.findProcessStationHandoff(orderCode, process).isPresent();
        }
        return documents.findProcessStationHandoff(orderCode, process, session).isPresent();
    }

    private String progressCode(String rawCode) {
        String text = rawCode == null ? "" : rawCode.trim();
        if (text.startsWith("JG|")) {
            return scanCodeParser.resolve(text, "pwa_report").code();
        }
        return text;
    }

    private String unfinishedProcess(StationTicket ticket) {
        for (String process : ticket.processes()) {
            if (!submitted(ticket.orderCode(), process)) {
                return process;
            }
        }
        return null;
    }

    private void assertMotherCustomer(MotherOrderRecord mother, AuthUserAccount account) {
        if (dataAccess != null) {
            dataAccess.assertCustomer(BusinessDataAccess.contextOf(account), mother.customerId(), blank(mother.customerName()));
        }
    }

    private StationTicket resolve(String rawCode) {
        String text = rawCode == null ? "" : rawCode.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请先扫贴纸");
        }
        if (text.startsWith("JG|")) {
            ScanResolveResponse resolved = scanCodeParser.resolve(text, "pwa_report");
            StationTicket fromCode = roundTicket(resolved.code());
            if (fromCode != null) {
                return fromCode;
            }
            if (resolved.objectType() == ScanObjectType.WORK_ORDER) {
                return childTicket(resolved.code());
            }
            if (resolved.objectType() == ScanObjectType.MOTHER_ORDER) {
                return motherTicket(resolved.code());
            }
            throw new BusinessException(WRONG_STICKER);
        }
        StationTicket round = roundTicket(text);
        if (round != null) {
            return round;
        }
        if (documents.findChildWorkOrderByCode(text).isPresent()) {
            return childTicket(text);
        }
        if (documents.findMotherOrderByCode(text).isPresent()) {
            return motherTicket(text);
        }
        throw new BusinessException(WRONG_STICKER);
    }

    /**
     * 重做轮次用新的工单号记账，库存维度仍落在原母单或子单上，避免把已有交付和余额改掉。
     */
    private StationTicket roundTicket(String code) {
        if (requirements == null || code == null || code.isBlank()) {
            return null;
        }
        ExecutionRound round = requirements.findRound(code).orElse(null);
        if (round == null) {
            return null;
        }
        boolean motherSource = round.sourceWorkOrderCode().isBlank()
                || round.sourceWorkOrderCode().equals(round.motherOrderCode());
        StationTicket source = motherSource ? motherTicket(round.motherOrderCode()) : childTicket(round.sourceWorkOrderCode());
        String tail = source.processes().isEmpty() ? round.processName() : source.processes().get(source.processes().size() - 1);
        return new StationTicket(
                round.roundWorkOrderCode(),
                source.motherOrderCode(),
                source.keptMother(),
                source.customerId(),
                source.ownerCustomerId(),
                source.ownerType(),
                source.customerName(),
                source.processingRequirement(),
                source.dueDate(),
                source.materialCode(),
                source.warehouseCode(),
                source.quantityUnit(),
                source.quantity(),
                source.weight(),
                List.of(round.processName()),
                source.stockOrderCode(),
                tail
        );
    }

    private StationTicket childTicket(String code) {
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code)
                .orElseThrow(() -> new BusinessException("子单不存在：" + code));
        MotherOrderRecord mother = requireMother(child.motherOrderCode());
        List<String> stored = WorkshopProcesses.split(child.processes());
        if (stored.isEmpty()) {
            throw new BusinessException("这张子单没有工序");
        }
        return overlay(new StationTicket(
                child.code(),
                mother.code(),
                false,
                child.customerId() == null ? mother.customerId() : child.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                ownerTypeOf(mother),
                blank(child.customerName()),
                blank(child.processingRequirement()),
                blank(child.dueDate()),
                blank(child.materialCode()),
                blank(child.warehouseCode()),
                blankToDefault(child.quantityUnit(), "件"),
                requirePositive(child.quantity(), "数量"),
                requirePositive(child.weight(), "重量"),
                WorkshopProcesses.normalize(stored)
        ));
    }

    private StationTicket motherTicket(String code) {
        MotherOrderRecord mother = requireMother(code);
        MotherSplitState state = documents.findMotherSplitState(mother.code()).orElse(null);
        if (!documents.listChildWorkOrders(mother.code()).isEmpty()
                || (state != null && MotherSplitState.SPLIT.equals(state.decision()))) {
            throw new BusinessException("这张母单已经拆成子单，请扫描子单贴纸");
        }
        if (state == null || !MotherSplitState.KEEP.equals(state.decision())) {
            throw new BusinessException("请先选择不拆，再扫母单贴纸");
        }
        return overlay(new StationTicket(
                mother.code(),
                mother.code(),
                true,
                mother.customerId(),
                mother.ownerCustomerId() == null ? mother.customerId() : mother.ownerCustomerId(),
                ownerTypeOf(mother),
                blank(mother.customerName()),
                blank(mother.processingRequirement()),
                blank(mother.dueDate()),
                blank(mother.materialCode()),
                blank(mother.warehouseCode()),
                blankToDefault(mother.quantityUnit(), "件"),
                requirePositive(mother.quantity(), "数量"),
                requirePositive(mother.weight(), "重量"),
                WorkshopProcesses.DEFAULT_ORDER
        ));
    }

    private void assertLabel(String rawCode, StationTicket ticket, String reason, AuthUserAccount account) {
        if (labelGuard != null) {
            labelGuard.check(rawCode, ticket.motherOrderCode(), ticket.orderCode(), reason, account);
        }
    }

    private StationTicket overlay(StationTicket ticket) {
        var version = OrderRequirementLookup.current(
                requirements, ticket.motherOrderCode(), ticket.keptMother() ? "" : ticket.orderCode()
        ).orElse(null);
        if (version == null) {
            return ticket;
        }
        return new StationTicket(
                ticket.orderCode(),
                ticket.motherOrderCode(),
                ticket.keptMother(),
                ticket.customerId(),
                ticket.ownerCustomerId(),
                ticket.ownerType(),
                ticket.customerName(),
                OrderRequirementLookup.requirement(version, ticket.processingRequirement()),
                OrderRequirementLookup.dueDate(version, ticket.dueDate()),
                ticket.materialCode(),
                ticket.warehouseCode(),
                ticket.quantityUnit(),
                ticket.quantity(),
                ticket.weight(),
                OrderRequirementLookup.route(version, ticket.processes())
        );
    }

    private StockDimension rawDimension(StationTicket ticket) {
        return new StockDimension(
                ticket.ownerType(),
                ticket.ownerCustomerId(),
                ticket.customerId(),
                ticket.motherOrderCode(),
                null,
                ticket.materialCode(),
                ticket.warehouseCode(),
                StockStatus.RAW_WAITING
        );
    }

    private StockDimension wipDimension(StationTicket ticket) {
        return new StockDimension(
                ticket.ownerType(),
                ticket.ownerCustomerId(),
                ticket.customerId(),
                ticket.motherOrderCode(),
                ticket.stockOrderCode(),
                ticket.materialCode(),
                ticket.warehouseCode(),
                StockStatus.WIP
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
            throw new BusinessException("请先登录后再做岗位报工");
        }
        if (!List.of(RoleCode.ADMIN, RoleCode.GENERAL_MANAGER, RoleCode.WORKSHOP_LEADER, RoleCode.OPERATOR).contains(account.role())) {
            throw new PermissionDeniedException("当前账号不是报工岗位");
        }
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

    private BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("这张单的" + label + "无效，不能报工");
        }
        return value;
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String text = blank(value);
        return text.isEmpty() ? fallback : text;
    }

    private record StationTicket(
            String orderCode,
            String motherOrderCode,
            boolean keptMother,
            Long customerId,
            Long ownerCustomerId,
            OwnerType ownerType,
            String customerName,
            String processingRequirement,
            String dueDate,
            String materialCode,
            String warehouseCode,
            String quantityUnit,
            BigDecimal quantity,
            BigDecimal weight,
            List<String> processes,
            String stockOrderCode,
            String routeTail
    ) {
        private StationTicket {
            processes = processes == null ? List.of() : List.copyOf(processes);
            if (stockOrderCode == null || stockOrderCode.isBlank()) {
                stockOrderCode = orderCode;
            }
            if (routeTail == null || routeTail.isBlank()) {
                routeTail = processes.isEmpty() ? "" : processes.get(processes.size() - 1);
            }
        }

        private StationTicket(
                String orderCode,
                String motherOrderCode,
                boolean keptMother,
                Long customerId,
                Long ownerCustomerId,
                OwnerType ownerType,
                String customerName,
                String processingRequirement,
                String dueDate,
                String materialCode,
                String warehouseCode,
                String quantityUnit,
                BigDecimal quantity,
                BigDecimal weight,
                List<String> processes
        ) {
            this(orderCode, motherOrderCode, keptMother, customerId, ownerCustomerId, ownerType, customerName,
                    processingRequirement, dueDate, materialCode, warehouseCode, quantityUnit, quantity, weight,
                    processes, "", "");
        }
    }
}
