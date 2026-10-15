package com.zhubao.erp.scan.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.BusinessDocumentService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.app.QualityRequirementService;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationCard;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.ProcessStationView;
import com.zhubao.erp.business.domain.QualityCheckLine;
import com.zhubao.erp.business.domain.QualityCheckView;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityRound;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.scan.domain.ScanBalanceCandidate;
import com.zhubao.erp.scan.domain.ScanContextAction;
import com.zhubao.erp.scan.domain.ScanContextOrder;
import com.zhubao.erp.scan.domain.ScanContextProgress;
import com.zhubao.erp.scan.domain.ScanContextRequest;
import com.zhubao.erp.scan.domain.ScanContextRequirements;
import com.zhubao.erp.scan.domain.ScanContextResponse;
import com.zhubao.erp.scan.domain.ScanContextTarget;
import com.zhubao.erp.scan.domain.ScanHandoffFact;
import com.zhubao.erp.scan.domain.ScanIntent;
import com.zhubao.erp.scan.domain.ScanObjectType;
import com.zhubao.erp.scan.domain.ScanOperationContext;
import com.zhubao.erp.scan.domain.ScanProgressRecord;
import com.zhubao.erp.scan.domain.ScanQualityFact;
import com.zhubao.erp.scan.domain.ScanQualityLineFact;
import com.zhubao.erp.scan.domain.ScanReinspectionStatus;
import com.zhubao.erp.scan.domain.ScanRelatedTarget;
import com.zhubao.erp.scan.domain.ScanRequirementItem;
import com.zhubao.erp.scan.domain.ScanShipmentFact;
import com.zhubao.erp.scan.domain.ScanStockFact;
import com.zhubao.erp.scan.domain.OperationProgressRepository;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一扫码上下文。只聚合已有查询，不写库存、工序、质检或出货单。
 */
@Service
public class ScanContextService {

    public static final String SELECT_CHILD = "SELECT_CHILD";
    public static final String INTAKE = "INTAKE";
    public static final String REPORT_PROCESS = "REPORT_PROCESS";
    public static final String SUBMIT_QUALITY = "SUBMIT_QUALITY";
    public static final String OPEN_REWORK = "OPEN_REWORK";
    public static final String REPORT_REWORK = "REPORT_REWORK";
    public static final String RELEASE = "RELEASE";
    public static final String SHIP = "SHIP";
    public static final String FILL_LOGISTICS = "FILL_LOGISTICS";
    public static final String BLOCK_NONE = "NONE";
    public static final String BLOCK_PERMISSION = "PERMISSION";
    public static final String BLOCK_STATE = "STATE";
    public static final String SOURCE_MOTHER = "MOTHER_ORDER";
    public static final String SOURCE_WORK_ORDER = "WORK_ORDER";
    public static final String SOURCE_STATION = "PROCESS";

    private static final String OUT_OF_SCOPE = "单号不在当前账号的业务数据范围内";
    private static final String NOT_FOUND = "未找到对应单据";
    private static final String REINSPECTION_PENDING = "当前没有待复检的不合格记录";
    private static final String REINSPECTION_KEPT = "原不合格记录仍保留，返工报工并复检合格后才能放行";
    private static final String CHOOSE_CHILD = "这张母单已经拆成子单，请选择子单";
    private static final String CHOOSE_BALANCE = "请选择完整的物料、仓库、货权和库存状态";
    private static final String ALREADY_SHIPPED = "这张出货单已经发货，不能再次扣库存";
    private static final ScanContextOrder EMPTY_ORDER = new ScanContextOrder(
            null, null, null, null, null, null, null, null, null, null);
    private static final ScanReinspectionStatus REINSPECTION_CLOSED =
            new ScanReinspectionStatus(false, REINSPECTION_PENDING);

    private final BusinessDocumentService documents;
    private final StockLedgerService stocks;
    private final BusinessDataAccess dataAccess;
    private final ProcessStationService stations;
    private final QualityRequirementService quality;
    private final PermissionGuard permissions;
    private final ScanCodeParser parser;
    private final OperationProgressRepository progressRecords;

    public ScanContextService(
            BusinessDocumentService documents,
            StockLedgerService stocks,
            BusinessDataAccess dataAccess,
            ProcessStationService stations,
            QualityRequirementService quality,
            PermissionGuard permissions,
            ScanCodeParser parser
    ) {
        this(documents, stocks, dataAccess, stations, quality, permissions, parser, null);
    }

    @Autowired
    public ScanContextService(
            BusinessDocumentService documents,
            StockLedgerService stocks,
            BusinessDataAccess dataAccess,
            ProcessStationService stations,
            QualityRequirementService quality,
            PermissionGuard permissions,
            ScanCodeParser parser,
            OperationProgressRepository progressRecords
    ) {
        this.documents = documents;
        this.stocks = stocks;
        this.dataAccess = dataAccess;
        this.stations = stations;
        this.quality = quality;
        this.permissions = permissions;
        this.parser = parser;
        this.progressRecords = progressRecords;
    }

    public ScanContextResponse query(ScanContextRequest request, AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new PermissionDeniedException("请先登录后再访问业务数据");
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException("请先扫贴纸");
        }
        OperatorContext operator = BusinessDataAccess.contextOf(account);
        ScanIntent intent = request.intent() == null ? ScanIntent.LOOKUP : request.intent();
        Resolution resolution = resolve(request.content().trim(), request.objectType(), intent, operator);
        if (resolution.options() != null) {
            return selection(resolution.options());
        }
        return assemble(resolution.hit(), account, intent);
    }

    private Resolution resolve(String content, ScanObjectType objectType, ScanIntent intent, OperatorContext operator) {
        if (content.startsWith("JG|")) {
            var parsed = parser.resolve(content, intent.name());
            if (objectType != null && objectType != parsed.objectType()) {
                throw new BusinessException("对象类型与贴纸不一致");
            }
            if (parsed.objectType() == ScanObjectType.EXPRESS_NO && intent != ScanIntent.SHIPPING) {
                return resolveExpress(parsed.code(), intent, operator);
            }
            Hit hit = loadExplicit(parsed.objectType(), parsed.code(), intent, operator);
            return Resolution.found(hit);
        }
        if (objectType != null) {
            String code = objectType == ScanObjectType.EXPRESS_NO ? waybillOf(content) : content.trim();
            if (objectType == ScanObjectType.EXPRESS_NO && code.isBlank()) {
                throw new BusinessException("未识别的快递单号");
            }
            return objectType == ScanObjectType.EXPRESS_NO && intent != ScanIntent.SHIPPING
                    ? resolveExpress(code, intent, operator)
                    : Resolution.found(loadExplicit(objectType, code, intent, operator));
        }
        List<Hit> found = exactHits(content);
        List<Hit> readable = found.stream().filter(hit -> readable(operator, hit)).toList();
        String waybill = CourierNumbers.extract(content);
        if (!readable.isEmpty()) {
            List<Hit> options = new ArrayList<>(readable);
            if (!waybill.isBlank() && intent != ScanIntent.SHIPPING) {
                addExpressOption(options, waybill, operator, intent);
            }
            if (intent == ScanIntent.SHIPPING && !waybill.isBlank() && readable.stream().noneMatch(hit -> hit.type() == ScanObjectType.EXPRESS_NO)) {
                options.add(expressHit(waybill, null));
            }
            List<Hit> unique = dedupe(options);
            if (unique.size() == 1) {
                return Resolution.found(unique.get(0));
            }
            return Resolution.choose(unique.stream().map(this::relatedOf).toList());
        }
        if (!found.isEmpty()) {
            throw new PermissionDeniedException(OUT_OF_SCOPE);
        }
        if (!waybill.isBlank()) {
            return intent == ScanIntent.SHIPPING
                    ? Resolution.found(loadExplicit(ScanObjectType.EXPRESS_NO, waybill, intent, operator))
                    : resolveExpress(waybill, intent, operator);
        }
        if (operator.dataScope() == DataScope.ALL) {
            throw new BusinessException("未识别的二维码内容");
        }
        throw new PermissionDeniedException(OUT_OF_SCOPE);
    }

    private Resolution resolveExpress(String waybill, ScanIntent intent, OperatorContext operator) {
        List<Hit> options = new ArrayList<>();
        addExpressOption(options, waybill, operator, intent);
        if (options.size() == 1) return Resolution.found(options.get(0));
        return Resolution.choose(options.stream().map(this::relatedOf).toList());
    }

    private void addExpressOption(List<Hit> options, String waybill, OperatorContext operator, ScanIntent intent) {
        List<MotherOrderRecord> found = documents.listMotherOrdersByCourierNo(waybill);
        for (MotherOrderRecord mother : found) {
            if (dataAccess.canRead(operator, mother.code())) {
                options.add(new Hit(ScanObjectType.MOTHER_ORDER, mother.code(), mother, null, null, null, false));
            }
        }
        if (intent == ScanIntent.INTAKE || found.isEmpty()) options.add(expressHit(waybill, null));
    }

    private Hit loadExplicit(ScanObjectType type, String code, ScanIntent intent, OperatorContext operator) {
        if (type == ScanObjectType.EXPRESS_NO) {
            if (intent == ScanIntent.SHIPPING) {
                return expressHit(code, null);
            }
            List<MotherOrderRecord> found = documents.listMotherOrdersByCourierNo(code);
            if (!found.isEmpty()) throw new BusinessException("运单关联母单，请选择具体母单");
            return expressHit(code, null);
        }
        List<Hit> found = exactHits(code).stream().filter(hit -> hit.type() == type).toList();
        if (found.isEmpty()) {
            if (operator.dataScope() == DataScope.ALL) {
                throw new BusinessException(NOT_FOUND);
            }
            throw new PermissionDeniedException(OUT_OF_SCOPE);
        }
        Hit hit = found.get(0);
        if (!readable(operator, hit)) {
            throw new PermissionDeniedException(OUT_OF_SCOPE);
        }
        return hit;
    }

    private List<Hit> exactHits(String code) {
        List<Hit> hits = new ArrayList<>();
        documents.findMotherOrder(code).ifPresent(mother -> hits.add(new Hit(
                ScanObjectType.MOTHER_ORDER, mother.code(), mother, null, null, null, false)));
        documents.findChildWorkOrder(code).ifPresent(child -> hits.add(new Hit(
                ScanObjectType.WORK_ORDER,
                child.code(),
                documents.findMotherOrder(child.motherOrderCode()).orElse(null),
                child,
                null,
                null,
                false)));
        documents.findOutboundOrder(code).ifPresent(outbound -> hits.add(new Hit(
                ScanObjectType.OUTBOUND_ORDER,
                outbound.code(),
                documents.findMotherOrder(outbound.motherOrderCode()).orElse(null),
                blank(outbound.workOrderCode()).isBlank() ? null : documents.findChildWorkOrder(outbound.workOrderCode()).orElse(null),
                outbound,
                null,
                false)));
        documents.findQualityByReworkOrderCode(code).ifPresent(check -> hits.add(new Hit(
                ScanObjectType.REWORK,
                check.reworkOrderCode(),
                documents.findMotherOrder(check.motherOrderCode()).orElse(null),
                documents.findChildWorkOrder(check.orderCode()).orElse(null),
                null,
                check,
                false)));
        return hits;
    }

    private Hit expressHit(String waybill, MotherOrderRecord mother) {
        return new Hit(ScanObjectType.EXPRESS_NO, waybill, mother, null, null, null, mother == null);
    }

    private boolean readable(OperatorContext operator, Hit hit) {
        String businessCode = switch (hit.type()) {
            case EXPRESS_NO -> hit.mother() == null ? "" : hit.mother().code();
            case MOTHER_ORDER -> hit.mother() == null ? "" : hit.mother().code();
            case WORK_ORDER -> hit.child() == null ? "" : hit.child().code();
            case OUTBOUND_ORDER -> hit.outbound() == null ? "" : hit.outbound().code();
            case REWORK -> hit.code();
        };
        if (businessCode.isBlank()) {
            return hit.type() == ScanObjectType.EXPRESS_NO && hit.newExpress();
        }
        return dataAccess.canRead(operator, businessCode);
    }

    private ScanContextResponse selection(List<ScanRelatedTarget> options) {
        return new ScanContextResponse(
                new ScanContextTarget(null, null, null, null, null, null, null),
                EMPTY_ORDER,
                new ScanContextRequirements(null, null, null, null, List.of()),
                emptyProgress(REINSPECTION_CLOSED),
                options,
                true,
                List.of(),
                new ScanOperationContext(List.of(), List.of(), true, null, null)
        );
    }

    private ScanContextResponse assemble(Hit hit, AuthUserAccount account, ScanIntent intent) {
        if (hit.type() == ScanObjectType.EXPRESS_NO && intent == ScanIntent.SHIPPING) {
            return logisticsOnly(hit.code());
        }
        if (hit.type() == ScanObjectType.EXPRESS_NO && hit.mother() == null) {
            return newExpress(hit.code(), account);
        }
        if (hit.type() == ScanObjectType.MOTHER_ORDER || (hit.type() == ScanObjectType.EXPRESS_NO && hit.mother() != null)) {
            return motherContext(hit, account);
        }
        if (hit.type() == ScanObjectType.WORK_ORDER) {
            return workOrderContext(hit, account);
        }
        if (hit.type() == ScanObjectType.REWORK) {
            return reworkContext(hit, account);
        }
        return outboundContext(hit, account);
    }

    private ScanContextResponse logisticsOnly(String waybill) {
        ScanContextTarget target = target(ScanObjectType.EXPRESS_NO, waybill, null, null, null, null);
        return new ScanContextResponse(
                target,
                EMPTY_ORDER,
                new ScanContextRequirements(null, null, null, null, List.of()),
                emptyProgress(REINSPECTION_CLOSED),
                List.of(),
                false,
                List.of(action(FILL_LOGISTICS, true, BLOCK_NONE, null)),
                new ScanOperationContext(List.of(), List.of(), false, null, target.reference())
        );
    }

    private ScanContextResponse newExpress(String waybill, AuthUserAccount account) {
        ScanContextTarget target = target(ScanObjectType.EXPRESS_NO, waybill, null, null, null, null);
        ScanContextAction intake = has(account, PermissionCode.BUSINESS_INBOUND_CONFIRM)
                ? action(INTAKE, true, BLOCK_NONE, null)
                : deny(INTAKE, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_INBOUND_CONFIRM));
        return new ScanContextResponse(
                target,
                EMPTY_ORDER,
                new ScanContextRequirements(null, null, null, null, List.of()),
                emptyProgress(REINSPECTION_CLOSED),
                List.of(),
                false,
                List.of(intake),
                operationOf(List.of(intake), List.of(), target.reference(), false)
        );
    }

    private ScanContextResponse motherContext(Hit hit, AuthUserAccount account) {
        MotherOrderRecord mother = hit.mother();
        boolean split = splitMother(mother.code());
        List<ChildWorkOrderRecord> children = documents.listChildWorkOrders(mother.code());
        List<ScanRelatedTarget> related = children.stream().map(child -> new ScanRelatedTarget(
                ScanObjectType.WORK_ORDER,
                child.code(),
                child.motherOrderCode(),
                emptyToNull(child.processingRequirement()),
                child.quantity(),
                emptyToNull(child.quantityUnit())
        )).toList();
        ScanContextTarget target = hit.type() == ScanObjectType.EXPRESS_NO
                ? target(ScanObjectType.EXPRESS_NO, hit.code(), mother.code(), null, null, null)
                : target(ScanObjectType.MOTHER_ORDER, mother.code(), mother.code(), null, null, null);
        ScanContextOrder order = orderOf(mother.customerId(), mother.customerName(), mother.goodsName(), mother.materialCode(),
                mother.quantity(), mother.quantityUnit(), mother.weight(), mother.processingRequirement(), mother.dueDate());
        ScanContextRequirements requirements = new ScanContextRequirements(
                emptyToNull(mother.processingRequirement()),
                SOURCE_MOTHER,
                null,
                null,
                split ? List.of() : stationRequirements("JG|mother|" + mother.code(), account));
        List<ScanBalanceCandidate> balances = balancesOf(mother.code(), null);
        ScanContextProgress progress = progressOf(mother.code(), null, null, balances);
        List<ScanContextAction> actions = new ArrayList<>();
        if (hit.type() == ScanObjectType.EXPRESS_NO) {
            actions.add(deny(INTAKE, BLOCK_STATE, "该运单已关联母单，请打开原单"));
        }
        if (split) {
            actions.add(action(SELECT_CHILD, true, BLOCK_NONE, null));
            actions.add(deny(REPORT_PROCESS, BLOCK_STATE, CHOOSE_CHILD));
            actions.add(deny(SUBMIT_QUALITY, BLOCK_STATE, CHOOSE_CHILD));
            actions.add(deny(SHIP, BLOCK_STATE, CHOOSE_CHILD));
            return respond(target, order, requirements, progress, related, true, actions, balances, true, target.reference());
        }
        String sticker = "JG|mother|" + mother.code();
        actions.add(reportAction(sticker, account));
        actions.add(qualityAction(sticker, account));
        actions.add(openReworkAction(mother.code(), account));
        actions.add(releaseAction(mother.code(), WorkshopProcesses.DEFAULT_ORDER, account));
        actions.add(shipAction(null, balances, account));
        return respond(target, order, requirements, progress, related, false, actions, balances, false, target.reference());
    }

    private ScanContextResponse workOrderContext(Hit hit, AuthUserAccount account) {
        ChildWorkOrderRecord child = hit.child();
        MotherOrderRecord mother = hit.mother();
        String sticker = "JG|work_order|" + child.code();
        ScanContextTarget target = target(ScanObjectType.WORK_ORDER, child.code(), child.motherOrderCode(), child.code(), null, null);
        ScanContextRequirements requirements = new ScanContextRequirements(
                mother == null ? null : emptyToNull(mother.processingRequirement()),
                mother == null ? null : SOURCE_MOTHER,
                emptyToNull(child.processingRequirement()),
                SOURCE_WORK_ORDER,
                stationRequirements(sticker, account));
        List<ScanBalanceCandidate> balances = balancesOf(child.motherOrderCode(), child.code());
        QualityRequirementCheck check = documents.findQualityRequirementCheck(child.code()).orElse(null);
        ScanContextProgress progress = progressOf(child.code(), check, null, balances);
        List<ScanContextAction> actions = new ArrayList<>();
        actions.add(reportAction(sticker, account));
        actions.add(qualityAction(sticker, account));
        actions.add(openReworkAction(child.code(), account));
        if (check != null && !blank(check.reworkOrderCode()).isBlank()) {
            actions.add(reworkReportAction(balances, account));
        }
        actions.add(releaseAction(child.code(), WorkshopProcesses.split(child.processes()), account));
        actions.add(shipAction(null, balances, account));
        ScanContextOrder order = orderOf(
                child.customerId() == null && mother != null ? mother.customerId() : child.customerId(),
                child.customerName(),
                child.goodsName(),
                child.materialCode(),
                child.quantity(),
                child.quantityUnit(),
                child.weight(),
                child.processingRequirement(),
                child.dueDate());
        return respond(target, order, requirements, progress, List.of(), false, actions, balances, false, target.reference());
    }

    private ScanContextResponse reworkContext(Hit hit, AuthUserAccount account) {
        QualityRequirementCheck check = hit.reworkCheck();
        ChildWorkOrderRecord child = hit.child();
        MotherOrderRecord mother = hit.mother();
        String orderCode = check.orderCode();
        ScanContextTarget target = target(
                ScanObjectType.REWORK, check.reworkOrderCode(), check.motherOrderCode(), orderCode, check.reworkOrderCode(), null);
        String workRequirement = child == null ? (mother == null ? null : mother.processingRequirement()) : child.processingRequirement();
        ScanContextRequirements requirements = new ScanContextRequirements(
                mother == null ? null : emptyToNull(mother.processingRequirement()),
                mother == null ? null : SOURCE_MOTHER,
                emptyToNull(workRequirement),
                child == null ? SOURCE_MOTHER : SOURCE_WORK_ORDER,
                List.of());
        List<ScanBalanceCandidate> balances = balancesOf(check.motherOrderCode(), orderCode);
        ScanContextProgress progress = progressOf(orderCode, check, null, balances);
        List<ScanContextAction> actions = List.of(
                deny(REPORT_PROCESS, BLOCK_STATE, "返工单不能按普通子单跳过返工规则"),
                deny(SUBMIT_QUALITY, BLOCK_STATE, "不能覆盖原不合格记录。返工报工后在质检面板提交复检"),
                reworkReportAction(balances, account),
                releaseAction(orderCode, child == null ? WorkshopProcesses.DEFAULT_ORDER : WorkshopProcesses.split(child.processes()), account)
        );
        ScanContextOrder order = child == null
                ? orderOf(mother == null ? null : mother.customerId(), mother == null ? null : mother.customerName(),
                mother == null ? null : mother.goodsName(), mother == null ? null : mother.materialCode(),
                mother == null ? null : mother.quantity(), mother == null ? null : mother.quantityUnit(),
                mother == null ? null : mother.weight(), workRequirement, mother == null ? null : mother.dueDate())
                : orderOf(child.customerId(), child.customerName(), child.goodsName(), child.materialCode(),
                child.quantity(), child.quantityUnit(), child.weight(), child.processingRequirement(), child.dueDate());
        return respond(target, order, requirements, progress, List.of(), false, actions, balances, false, target.reference());
    }

    private ScanContextResponse outboundContext(Hit hit, AuthUserAccount account) {
        OutboundOrderRecord outbound = hit.outbound();
        ChildWorkOrderRecord child = hit.child();
        MotherOrderRecord mother = hit.mother();
        String requirement = child != null ? child.processingRequirement() : (mother == null ? null : mother.processingRequirement());
        ScanContextTarget target = target(
                ScanObjectType.OUTBOUND_ORDER,
                outbound.code(),
                outbound.motherOrderCode(),
                blank(outbound.workOrderCode()),
                null,
                outbound.code());
        ScanContextRequirements requirements = new ScanContextRequirements(
                mother == null ? null : emptyToNull(mother.processingRequirement()),
                mother == null ? null : SOURCE_MOTHER,
                child == null ? null : emptyToNull(child.processingRequirement()),
                child == null ? null : SOURCE_WORK_ORDER,
                List.of());
        List<ScanBalanceCandidate> balances = balancesOf(outbound.motherOrderCode(), blank(outbound.workOrderCode()));
        ScanContextProgress progress = progressOf(blank(outbound.workOrderCode()), null, outbound, balances);
        List<ScanContextAction> actions = List.of(shipAction(outbound, balances, account));
        ScanContextOrder order = orderOf(
                outbound.customerId(),
                mother == null ? null : mother.customerName(),
                child != null ? child.goodsName() : (mother == null ? null : mother.goodsName()),
                outbound.materialCode(),
                outbound.quantity(),
                outbound.quantityUnit(),
                outbound.weight(),
                requirement,
                outbound.deliveryDate());
        return respond(target, order, requirements, progress, List.of(), false, actions, balances, false, target.reference());
    }

    private ScanContextResponse respond(
            ScanContextTarget target,
            ScanContextOrder order,
            ScanContextRequirements requirements,
            ScanContextProgress progress,
            List<ScanRelatedTarget> related,
            boolean requiresSelection,
            List<ScanContextAction> actions,
            List<ScanBalanceCandidate> balances,
            boolean forceBalanceSelection,
            String reference
    ) {
        boolean selection = forceBalanceSelection || balances.size() > 1;
        return new ScanContextResponse(
                target,
                order,
                requirements,
                progress,
                related,
                requiresSelection,
                actions,
                operationOf(actions, balances, snapshot(reference, progress), selection)
        );
    }

    private ScanOperationContext operationOf(
            List<ScanContextAction> actions,
            List<ScanBalanceCandidate> balances,
            String snapshot,
            boolean forceSelection
    ) {
        boolean selection = forceSelection || balances.size() > 1;
        List<String> formal = actions.stream()
                .filter(ScanContextAction::allowed)
                .map(ScanContextAction::code)
                .filter(code -> !SELECT_CHILD.equals(code) && !FILL_LOGISTICS.equals(code))
                .toList();
        ScanBalanceCandidate selected = selection || balances.size() != 1 ? null : balances.get(0);
        return new ScanOperationContext(formal, balances, selection, selected, snapshot);
    }

    private ScanContextAction reportAction(String sticker, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_PRODUCTION_REPORT)) {
            return deny(REPORT_PROCESS, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_PRODUCTION_REPORT));
        }
        try {
            ProcessStationView view = stations.lookup(sticker, account);
            if (view.stations() == null || view.stations().isEmpty()) {
                String reason = blank(view.message()).isBlank() ? "当前账号未绑定这张单上的工序，不能报工" : view.message();
                return deny(REPORT_PROCESS, BLOCK_PERMISSION, reason);
            }
            for (ProcessStationCard card : view.stations()) {
                if (!card.submitted() && (blank(card.previousProcessName()).isBlank() || card.previousSubmitted())) {
                    return action(REPORT_PROCESS, true, BLOCK_NONE, null);
                }
            }
            ProcessStationCard blocked = view.stations().stream().filter(card -> !card.submitted()).findFirst().orElse(null);
            if (blocked != null && !blank(blocked.previousProcessName()).isBlank() && !blocked.previousSubmitted()) {
                return deny(REPORT_PROCESS, BLOCK_STATE,
                        "上一道「" + blocked.previousProcessName() + "」还没交，不能交「" + blocked.processName() + "」");
            }
            return deny(REPORT_PROCESS, BLOCK_STATE, "当前可见工序都已交付");
        } catch (PermissionDeniedException ex) {
            return deny(REPORT_PROCESS, BLOCK_PERMISSION, ex.getMessage());
        } catch (BusinessException ex) {
            return deny(REPORT_PROCESS, BLOCK_STATE, ex.getMessage());
        }
    }

    private List<ScanRequirementItem> stationRequirements(String sticker, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_PRODUCTION_REPORT)) {
            return List.of();
        }
        try {
            ProcessStationView view = stations.lookup(sticker, account);
            List<ScanRequirementItem> items = new ArrayList<>();
            for (ProcessStationCard card : view.stations()) {
                items.add(new ScanRequirementItem(SOURCE_STATION, card.processName(), emptyToNull(card.requirement())));
            }
            return items;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private ScanContextAction qualityAction(String sticker, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_QUALITY_FREEZE)) {
            return deny(SUBMIT_QUALITY, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_QUALITY_FREEZE));
        }
        try {
            QualityCheckView view = quality.lookup(sticker, account);
            if (!blank(view.qualityCaseCode()).isBlank()) {
                return deny(SUBMIT_QUALITY, BLOCK_STATE, "这张单已经做过质检对照，不能覆盖原结果");
            }
            if (!view.ready()) {
                String reason = blank(view.blockReason()).isBlank() ? "当前不能提交质检" : view.blockReason();
                return deny(SUBMIT_QUALITY, BLOCK_STATE, reason);
            }
            return action(SUBMIT_QUALITY, true, BLOCK_NONE, null);
        } catch (PermissionDeniedException ex) {
            return deny(SUBMIT_QUALITY, BLOCK_PERMISSION, ex.getMessage());
        } catch (BusinessException ex) {
            return deny(SUBMIT_QUALITY, BLOCK_STATE, ex.getMessage());
        }
    }

    private ScanContextAction openReworkAction(String orderCode, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_REWORK_DISPATCH)) {
            return deny(OPEN_REWORK, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_REWORK_DISPATCH));
        }
        QualityRequirementCheck check = documents.findQualityRequirementCheck(orderCode).orElse(null);
        if (check == null) {
            return deny(OPEN_REWORK, BLOCK_STATE, "请先提交质检对照");
        }
        if (!QualityVerdicts.FAIL.equals(check.outcome())) {
            return deny(OPEN_REWORK, BLOCK_STATE, "合格或让步不能开返工");
        }
        if (!blank(check.reworkOrderCode()).isBlank()) {
            return deny(OPEN_REWORK, BLOCK_STATE, "已经开过返工");
        }
        boolean frozen = balancesOf(check.motherOrderCode(), orderCode).stream()
                .anyMatch(balance -> StockStatus.FROZEN.name().equals(balance.stockStatus())
                        && balance.quantity().compareTo(BigDecimal.ZERO) > 0);
        if (!frozen) {
            return deny(OPEN_REWORK, BLOCK_STATE, "冻结库存不存在，不能开返工");
        }
        return action(OPEN_REWORK, true, BLOCK_NONE, null);
    }

    private ScanContextAction reworkReportAction(List<ScanBalanceCandidate> balances, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_REWORK_REPORT)) {
            return deny(REPORT_REWORK, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_REWORK_REPORT));
        }
        List<ScanBalanceCandidate> rework = balances.stream()
                .filter(balance -> StockStatus.REWORK.name().equals(balance.stockStatus()))
                .toList();
        if (rework.isEmpty()) {
            return deny(REPORT_REWORK, BLOCK_STATE, "没有可报工的返工库存");
        }
        if (rework.size() > 1) {
            return deny(REPORT_REWORK, BLOCK_STATE, CHOOSE_BALANCE);
        }
        return action(REPORT_REWORK, true, BLOCK_NONE, null);
    }

    private ScanContextAction releaseAction(String orderCode, List<String> processes, AuthUserAccount account) {
        if (!has(account, PermissionCode.BUSINESS_QUALITY_RELEASE)) {
            return deny(RELEASE, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_QUALITY_RELEASE));
        }
        QualityRequirementCheck check = documents.findQualityRequirementCheck(orderCode).orElse(null);
        if (check == null) {
            return deny(RELEASE, BLOCK_STATE, "请先完成质检对照，不能直接放行");
        }
        if (!releasable(orderCode, check)) {
            return deny(RELEASE, BLOCK_STATE, "只有真实合格或让步的质检结果才能放行。" + REINSPECTION_KEPT);
        }
        if (processes == null || processes.isEmpty()) {
            return deny(RELEASE, BLOCK_STATE, "最后一道工序还没完成，不能提交质检");
        }
        String last = processes.get(processes.size() - 1);
        boolean submitted = documents.listProcessStationHandoffs(orderCode).stream()
                .anyMatch(handoff -> last.equals(handoff.processName()));
        if (!submitted) {
            return deny(RELEASE, BLOCK_STATE, "最后一道工序还没完成，不能提交质检");
        }
        return action(RELEASE, true, BLOCK_NONE, null);
    }

    private ScanContextAction shipAction(OutboundOrderRecord outbound, List<ScanBalanceCandidate> balances, AuthUserAccount account) {
        if (outbound != null && "SHIPPED".equals(outbound.status())) {
            return deny(SHIP, BLOCK_STATE, ALREADY_SHIPPED);
        }
        if (!has(account, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT)) {
            return deny(SHIP, BLOCK_PERMISSION, permissionReason(account, PermissionCode.BUSINESS_OUTBOUND_SHIPMENT));
        }
        if (balances.size() > 1) {
            return deny(SHIP, BLOCK_STATE, CHOOSE_BALANCE);
        }
        List<ScanBalanceCandidate> finished = balances.stream()
                .filter(balance -> StockStatus.FINISHED.name().equals(balance.stockStatus()))
                .toList();
        if (finished.size() != 1) {
            return deny(SHIP, BLOCK_STATE, finished.isEmpty() ? "没有可发货的成品余额" : CHOOSE_BALANCE);
        }
        return action(SHIP, true, BLOCK_NONE, null);
    }

    private boolean releasable(String orderCode, QualityRequirementCheck check) {
        QualityRound latest = latestRound(orderCode, check);
        if (latest == null) {
            return false;
        }
        if (latest.frozen()) {
            return false;
        }
        return QualityVerdicts.PASS.equals(latest.outcome()) || QualityVerdicts.CONCESSION.equals(latest.outcome());
    }

    private ScanReinspectionStatus reinspectionOf(String orderCode, QualityRequirementCheck check) {
        if (check == null || (!check.frozen() && !QualityVerdicts.FAIL.equals(check.outcome()))) {
            return REINSPECTION_CLOSED;
        }
        QualityRound latest = latestRound(orderCode, check);
        if (latest != null
                && QualityRound.RECHECK.equals(latest.roundType())
                && !latest.frozen()
                && (QualityVerdicts.PASS.equals(latest.outcome()) || QualityVerdicts.CONCESSION.equals(latest.outcome()))) {
            return new ScanReinspectionStatus(true, "最新复检可以放行，原不合格记录仍保留");
        }
        return new ScanReinspectionStatus(false, REINSPECTION_KEPT);
    }

    private QualityRound latestRound(String orderCode, QualityRequirementCheck check) {
        List<QualityRound> stored = documents.listQualityRounds(orderCode);
        QualityRound latest = null;
        for (QualityRound round : stored) {
            if (latest == null || round.roundNo() > latest.roundNo()) {
                latest = round;
            }
        }
        if (latest != null) {
            return latest;
        }
        if (check == null) {
            return null;
        }
        return new QualityRound(
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
                List.of()
        );
    }

    private ScanContextProgress progressOf(
            String handoffOrderCode,
            QualityRequirementCheck check,
            OutboundOrderRecord outbound,
            List<ScanBalanceCandidate> balances
    ) {
        List<ScanHandoffFact> handoffs = blank(handoffOrderCode).isBlank()
                ? List.of()
                : documents.listProcessStationHandoffs(handoffOrderCode).stream()
                .map(this::handoffFact)
                .toList();
        ScanQualityFact qualityFact = check == null ? null : qualityFact(check);
        ScanReinspectionStatus reinspection = reinspectionOf(handoffOrderCode, check);
        List<ScanStockFact> stock = balances.stream().map(balance -> new ScanStockFact(
                balance.materialCode(),
                balance.warehouseCode(),
                balance.ownerType(),
                balance.ownerCustomerId(),
                balance.workOrderCode(),
                balance.stockStatus(),
                balance.quantity(),
                balance.weight()
        )).toList();
        return new ScanContextProgress(handoffs, qualityFact, reinspection, stock, shipmentsOf(outbound, balances), loadProgress(handoffOrderCode));
    }

    /** 按真实工单带回已保存的过程说明。没有记录时为空，不编造。 */
    private List<ScanProgressRecord> loadProgress(String orderCode) {
        if (progressRecords == null || blank(orderCode).isBlank()) {
            return List.of();
        }
        String targetType = documents.findChildWorkOrder(orderCode).isPresent()
                ? EvidenceCodes.WORK_ORDER
                : EvidenceCodes.MOTHER_ORDER;
        return progressRecords.findByTarget(targetType, orderCode).stream()
                .map(record -> record.toScan())
                .toList();
    }

    private List<ScanShipmentFact> shipmentsOf(OutboundOrderRecord outbound, List<ScanBalanceCandidate> balances) {
        Map<String, ScanShipmentFact> shipments = new LinkedHashMap<>();
        if (outbound != null) {
            shipments.put(outbound.code(), new ScanShipmentFact(
                    outbound.code(),
                    outbound.motherOrderCode(),
                    blank(outbound.workOrderCode()),
                    emptyToNull(outbound.logisticsNo()),
                    outbound.status(),
                    emptyToNull(outbound.ledgerCode())
            ));
        }
        for (StockLedgerEntry entry : stocks.ledgers()) {
            if (!"outbound_shipment".equals(entry.documentType()) || blank(entry.documentCode()).isBlank()) {
                continue;
            }
            if (entry.to() == null || balances.stream().noneMatch(balance -> sameOrder(balance, entry.to()))) {
                continue;
            }
            shipments.putIfAbsent(entry.documentCode(), new ScanShipmentFact(
                    entry.documentCode(),
                    entry.to().motherOrderCode(),
                    blank(entry.to().workOrderCode()),
                    null,
                    StockStatus.SHIPPED.name(),
                    entry.code()
            ));
        }
        return List.copyOf(shipments.values());
    }

    private boolean sameOrder(ScanBalanceCandidate balance, StockDimension dimension) {
        return Objects.equals(blank(balance.motherOrderCode()), blank(dimension.motherOrderCode()))
                && Objects.equals(blank(balance.workOrderCode()), blank(dimension.workOrderCode()));
    }

    private ScanHandoffFact handoffFact(ProcessStationHandoff handoff) {
        return new ScanHandoffFact(handoff.processName(), handoff.operator(), handoff.ledgerCode(), handoff.createdAt());
    }

    private ScanQualityFact qualityFact(QualityRequirementCheck check) {
        List<ScanQualityLineFact> lines = new ArrayList<>();
        for (QualityCheckLine line : check.lines()) {
            lines.add(new ScanQualityLineFact(line.lineNo(), line.clause(), emptyToNull(line.verdict()), emptyToNull(line.reason())));
        }
        return new ScanQualityFact(
                check.qualityCaseCode(),
                check.orderCode(),
                check.outcome(),
                check.frozen(),
                emptyToNull(check.reworkOrderCode()),
                check.inspector(),
                lines
        );
    }

    private List<ScanBalanceCandidate> balancesOf(String motherOrderCode, String workOrderCode) {
        String mother = blank(motherOrderCode);
        String work = blank(workOrderCode);
        return stocks.balances().stream()
                .filter(balance -> balance.quantity().compareTo(BigDecimal.ZERO) > 0 || balance.weight().compareTo(BigDecimal.ZERO) > 0)
                .filter(balance -> {
                    StockDimension dimension = balance.dimension();
                    if (!work.isBlank()) {
                        return work.equals(blank(dimension.workOrderCode()));
                    }
                    return mother.equals(blank(dimension.motherOrderCode())) && blank(dimension.workOrderCode()).isBlank();
                })
                .map(this::candidate)
                .sorted(Comparator.comparing(ScanBalanceCandidate::warehouseCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ScanBalanceCandidate::stockStatus, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private ScanBalanceCandidate candidate(StockBalance balance) {
        StockDimension dimension = balance.dimension();
        return new ScanBalanceCandidate(
                dimension.materialCode(),
                dimension.warehouseCode(),
                dimension.ownerType().name(),
                dimension.ownerCustomerId(),
                dimension.customerId(),
                dimension.motherOrderCode(),
                blank(dimension.workOrderCode()),
                dimension.stockStatus().name(),
                balance.quantity(),
                balance.weight()
        );
    }

    private boolean splitMother(String motherOrderCode) {
        if (!documents.listChildWorkOrders(motherOrderCode).isEmpty()) {
            return true;
        }
        return documents.findMotherSplitState(motherOrderCode)
                .map(state -> MotherSplitState.SPLIT.equals(state.decision()))
                .orElse(false);
    }

    private ScanRelatedTarget relatedOf(Hit hit) {
        String requirement = null;
        BigDecimal quantity = null;
        String unit = null;
        String motherCode = hit.mother() == null ? null : hit.mother().code();
        if (hit.child() != null) {
            requirement = emptyToNull(hit.child().processingRequirement());
            quantity = hit.child().quantity();
            unit = emptyToNull(hit.child().quantityUnit());
            motherCode = hit.child().motherOrderCode();
        } else if (hit.mother() != null && hit.type() != ScanObjectType.EXPRESS_NO) {
            requirement = emptyToNull(hit.mother().processingRequirement());
            quantity = hit.mother().quantity();
            unit = emptyToNull(hit.mother().quantityUnit());
        }
        return new ScanRelatedTarget(hit.type(), hit.code(), motherCode, requirement, quantity, unit);
    }

    private List<Hit> dedupe(List<Hit> hits) {
        Map<String, Hit> unique = new LinkedHashMap<>();
        for (Hit hit : hits) {
            unique.putIfAbsent(hit.type().name() + ":" + hit.code(), hit);
        }
        return List.copyOf(unique.values());
    }

    private String waybillOf(String content) {
        String extracted = CourierNumbers.extract(content);
        return extracted.isBlank() ? "" : extracted;
    }

    private ScanContextTarget target(
            ScanObjectType type,
            String code,
            String motherOrderCode,
            String workOrderCode,
            String reworkOrderCode,
            String outboundOrderCode
    ) {
        return new ScanContextTarget(
                type,
                emptyToNull(code),
                emptyToNull(motherOrderCode),
                emptyToNull(workOrderCode),
                emptyToNull(reworkOrderCode),
                emptyToNull(outboundOrderCode),
                type.name() + ":" + blank(code)
        );
    }

    private ScanContextOrder orderOf(
            Long customerId,
            String customerName,
            String goodsName,
            String materialCode,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal weight,
            String requirement,
            String dueDate
    ) {
        return new ScanContextOrder(
                customerId,
                emptyToNull(customerName),
                emptyToNull(goodsName),
                emptyToNull(materialCode),
                quantity,
                emptyToNull(quantityUnit),
                weight,
                null,
                emptyToNull(requirement),
                emptyToNull(dueDate)
        );
    }

    private ScanContextProgress emptyProgress(ScanReinspectionStatus reinspection) {
        return new ScanContextProgress(List.of(), null, reinspection, List.of(), List.of(), List.of());
    }

    private String snapshot(String reference, ScanContextProgress progress) {
        List<String> parts = new ArrayList<>();
        parts.add(blank(reference));
        progress.handoffs().stream().map(ScanHandoffFact::ledgerCode).sorted().forEach(parts::add);
        if (progress.quality() != null) {
            parts.add(blank(progress.quality().qualityCaseCode()));
        }
        progress.shipments().stream().map(ScanShipmentFact::outboundCode).sorted().forEach(parts::add);
        return String.join("|", parts);
    }

    private boolean has(AuthUserAccount account, PermissionCode permission) {
        return permissions.allowedPermissionsFor(account.role()).contains(permission);
    }

    private String permissionReason(AuthUserAccount account, PermissionCode permission) {
        return "角色 " + account.role() + " 无权执行 " + permission.label();
    }

    private ScanContextAction action(String code, boolean allowed, String kind, String reason) {
        return new ScanContextAction(code, allowed, kind, reason);
    }

    private ScanContextAction deny(String code, String kind, String reason) {
        return new ScanContextAction(code, false, kind, reason);
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        String text = blank(value);
        return text.isEmpty() ? null : text;
    }

    private record Hit(
            ScanObjectType type,
            String code,
            MotherOrderRecord mother,
            ChildWorkOrderRecord child,
            OutboundOrderRecord outbound,
            QualityRequirementCheck reworkCheck,
            boolean newExpress
    ) {
    }

    private record Resolution(Hit hit, List<ScanRelatedTarget> options) {
        private static Resolution found(Hit hit) {
            return new Resolution(hit, null);
        }

        private static Resolution choose(List<ScanRelatedTarget> options) {
            return new Resolution(null, options);
        }
    }
}
