package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderWorkspaceAccess;
import com.zhubao.erp.business.domain.OrderWorkspacePage;
import com.zhubao.erp.business.domain.OrderWorkspaceQuery;
import com.zhubao.erp.business.domain.OrderWorkspaceRow;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessProgressRepository;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.domain.QualityVerdicts;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单中心只读查询。一次装入单据、进度、轮次和库存余额，不写任何业务事实。
 */
@Service
public class OrderWorkspaceQueryService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<Integer> PERCENTS = Set.of(0, 30, 60, 90, 100);
    private static final String MOTHER = "MOTHER_ORDER";
    private static final String WORK = "WORK_ORDER";
    private static final String WAITING_SPLIT = "待拆分";
    private static final String WAITING_START = "待开工";
    private static final String IN_PROCESS = "在制";
    private static final String WAITING_QUALITY = "待质检";
    private static final String QUALITY_DONE = "已质检";
    private static final String REWORK = "返工";

    private final BusinessDocumentRepository documents;
    private final AuthUserAccountRepository accounts;
    private final ProcessProgressRepository progress;
    private final OrderRequirementRepository requirements;
    private final StockLedgerService stocks;

    public OrderWorkspaceQueryService(BusinessDocumentRepository documents) {
        this(documents, null, null, null, null);
    }

    @Autowired
    public OrderWorkspaceQueryService(
            BusinessDocumentRepository documents,
            AuthUserAccountRepository accounts,
            ProcessProgressRepository progress,
            OrderRequirementRepository requirements,
            StockLedgerService stocks
    ) {
        this.documents = documents;
        this.accounts = accounts;
        this.progress = progress;
        this.requirements = requirements;
        this.stocks = stocks;
    }

    public OrderWorkspacePage query(OperatorContext operator, OrderWorkspaceAccess access, OrderWorkspaceQuery query) {
        List<StockBalance> balances = stocks == null ? List.of() : List.copyOf(stocks.balances());
        return queryWithBalances(operator, access, query, balances);
    }

    OrderWorkspacePage queryWithBalances(
            OperatorContext operator,
            OrderWorkspaceAccess access,
            OrderWorkspaceQuery query,
            List<StockBalance> balances
    ) {
        if (operator == null) {
            throw new PermissionDeniedException("请先登录后再访问业务数据");
        }
        OrderWorkspaceAccess granted = access == null ? new OrderWorkspaceAccess(List.of(), List.of()) : access;
        List<String> views = availableViews(granted.menuCodes());
        if (views.isEmpty() || !views.contains(query.view())) {
            throw new PermissionDeniedException("当前账号不能查看这个订单视图");
        }
        Loaded loaded = visibleRows(operator, balances);
        Map<String, List<com.zhubao.erp.business.domain.ProcessBatchHandoff>> batchRows = new LinkedHashMap<>();
        for (var batch : documents.listAllProcessBatches()) batchRows.computeIfAbsent(batch.orderCode(), key -> new ArrayList<>()).add(batch);
        List<OrderWorkspaceRow> rows = loaded.rows().stream()
                .map(row -> batchRow(row, query, granted.allowedProcesses(), batchRows.getOrDefault(row.code(), List.of())))
                .filter(row -> matchesView(query.view(), row, granted.allowedProcesses()))
                .filter(row -> matchesQuery(query.query(), row, loaded))
                .filter(row -> query.customerId() == null || query.customerId().equals(row.customerId()))
                .filter(row -> query.process().isEmpty() || query.process().equals(blank(row.currentProcess())))
                .filter(row -> matchesDue(query.dueBefore(), row.dueDate()))
                .toList();
        Map<String, Integer> counts = facetCounts(rows);
        List<OrderWorkspaceRow> selected = rows.stream()
                .filter(row -> OrderWorkspaceQuery.ALL.equals(query.facet()) || query.facet().equals(row.facet()))
                .sorted(ROW_ORDER)
                .toList();
        int from = Math.min((query.page() - 1) * query.size(), selected.size());
        int to = Math.min(from + query.size(), selected.size());
        return new OrderWorkspacePage(
                List.copyOf(selected.subList(from, to)),
                query.page(),
                query.size(),
                selected.size(),
                counts,
                views,
                OffsetDateTime.now(SHANGHAI).toString()
        );
    }

    private Loaded visibleRows(OperatorContext operator, List<StockBalance> balances) {
        Map<String, MotherSplitState> splits = new LinkedHashMap<>();
        for (MotherSplitState state : documents.listAllMotherSplitStates()) {
            splits.putIfAbsent(state.motherOrderCode(), state);
        }
        Map<String, List<ChildWorkOrderRecord>> children = new LinkedHashMap<>();
        for (ChildWorkOrderRecord child : documents.listAllChildWorkOrders()) {
            children.computeIfAbsent(child.motherOrderCode(), key -> new ArrayList<>()).add(child);
        }
        Map<String, Set<String>> submitted = new LinkedHashMap<>();
        Map<String, String> operators = new LinkedHashMap<>();
        Map<String, String> handoffTimes = new LinkedHashMap<>();
        for (ProcessStationHandoff handoff : documents.listAllProcessStationHandoffs()) {
            if (handoff.orderCode().startsWith("FG-")) {
                continue;
            }
            submitted.computeIfAbsent(handoff.orderCode(), key -> new LinkedHashSet<>()).add(handoff.processName());
            operators.put(handoff.orderCode() + "|" + handoff.processName(), blank(handoff.operator()));
            handoffTimes.put(handoff.orderCode() + "|" + handoff.processName(), blank(handoff.createdAt()));
        }
        Map<String, QualityRequirementCheck> checks = new LinkedHashMap<>();
        for (QualityRequirementCheck check : documents.listAllQualityRequirementChecks()) {
            checks.put(check.orderCode(), check);
        }
        Map<String, ProcessProgressHead> heads = new LinkedHashMap<>();
        if (progress != null) {
            for (ProcessProgressHead head : progress.listAll()) {
                heads.put(head.orderCode() + "|" + head.processName(), head);
            }
        }
        Set<String> shipped = new LinkedHashSet<>();
        Map<String, Set<String>> logistics = new LinkedHashMap<>();
        for (OutboundOrderRecord order : documents.listOutboundOrders()) {
            if ("SHIPPED".equals(blank(order.status()))) {
                shipped.add(orderKey(order.workOrderCode(), order.motherOrderCode()));
            }
            if (!blank(order.logisticsNo()).isEmpty()) {
                logistics.computeIfAbsent(blank(order.logisticsNo()), key -> new LinkedHashSet<>())
                        .add(orderKey(order.workOrderCode(), order.motherOrderCode()));
            }
        }
        Map<String, BigDecimal> finished = new LinkedHashMap<>();
        for (StockBalance balance : balances == null ? List.<StockBalance>of() : balances) {
            if (balance == null || balance.dimension() == null || balance.dimension().stockStatus() != StockStatus.FINISHED) {
                continue;
            }
            if (balance.quantity() == null || balance.quantity().signum() <= 0) {
                continue;
            }
            String key = orderKey(balance.dimension().workOrderCode(), balance.dimension().motherOrderCode());
            finished.merge(key, balance.quantity(), BigDecimal::add);
        }
        List<ExecutionRound> rounds = requirements == null ? List.of() : requirements.listAll();
        List<OrderWorkspaceRow> rows = new ArrayList<>();
        Set<String> visibleCodes = new LinkedHashSet<>();
        Map<String, MotherOrderRecord> mothers = new LinkedHashMap<>();
        Map<String, String> courierByCode = new LinkedHashMap<>();
        for (MotherOrderRecord mother : documents.listMotherOrders()) {
            mothers.put(mother.code(), mother);
            if (mother.quantity() == null || mother.quantity().signum() <= 0) {
                continue;
            }
            if (!withinScope(operator, mother)) {
                continue;
            }
            List<ChildWorkOrderRecord> family = children.getOrDefault(mother.code(), List.of());
            MotherSplitState split = splits.get(mother.code());
            boolean splitDecision = split != null && MotherSplitState.SPLIT.equals(split.decision());
            if (family.isEmpty() && !splitDecision) {
                rows.add(motherRow(mother, split != null && MotherSplitState.KEEP.equals(split.decision()), submitted, checks, heads, operators, handoffTimes, shipped, finished, logistics));
                visibleCodes.add(mother.code());
                courierByCode.put(mother.code(), blank(mother.courierNo()));
                continue;
            }
            for (ChildWorkOrderRecord child : family) {
                if (CustomerScopeRules.identityConflicts(child.customerId(), mother.customerId())) {
                    continue;
                }
                rows.add(childRow(mother, child, submitted, checks, heads, operators, handoffTimes, shipped, finished, logistics));
                visibleCodes.add(child.code());
                courierByCode.put(child.code(), blank(mother.courierNo()));
            }
            visibleCodes.add(mother.code());
            courierByCode.put(mother.code(), blank(mother.courierNo()));
        }
        for (ExecutionRound round : rounds) {
            if (!visibleCodes.contains(round.motherOrderCode()) || blank(round.roundWorkOrderCode()).isEmpty()) {
                continue;
            }
            MotherOrderRecord mother = mothers.get(round.motherOrderCode());
            if (mother == null) {
                continue;
            }
            rows.add(roundRow(mother, round, submitted, checks, heads, operators, handoffTimes, shipped, finished, logistics, visibleCodes));
            courierByCode.put(round.roundWorkOrderCode(), blank(mother.courierNo()));
        }
        return new Loaded(rows, courierByCode, logistics);
    }

    private OrderWorkspaceRow motherRow(
            MotherOrderRecord mother,
            boolean kept,
            Map<String, Set<String>> submitted,
            Map<String, QualityRequirementCheck> checks,
            Map<String, ProcessProgressHead> heads,
            Map<String, String> operators,
            Map<String, String> handoffTimes,
            Set<String> shipped,
            Map<String, BigDecimal> finished,
            Map<String, Set<String>> logistics
    ) {
        if (!kept) {
            return build(
                    MOTHER, mother.code(), mother.code(), null, null, "来料", mother,
                    mother.goodsName(), mother.materialCode(), mother.quantity(), mother.quantityUnit(), mother.weight(),
                    mother.dueDate(), WAITING_SPLIT, "未质检", null, null, null, null, null,
                    shipped, finished, logistics, mother.courierNo()
            );
        }
        return station(
                MOTHER, mother.code(), mother.code(), null, null, mother,
                mother.goodsName(), mother.materialCode(), mother.quantity(), mother.quantityUnit(), mother.weight(),
                mother.dueDate(), WorkshopProcesses.DEFAULT_ORDER, submitted, checks, heads, operators, handoffTimes,
                shipped, finished, logistics, mother.courierNo()
        );
    }

    private OrderWorkspaceRow childRow(
            MotherOrderRecord mother,
            ChildWorkOrderRecord child,
            Map<String, Set<String>> submitted,
            Map<String, QualityRequirementCheck> checks,
            Map<String, ProcessProgressHead> heads,
            Map<String, String> operators,
            Map<String, String> handoffTimes,
            Set<String> shipped,
            Map<String, BigDecimal> finished,
            Map<String, Set<String>> logistics
    ) {
        String due = blank(child.dueDate()).isEmpty() ? mother.dueDate() : child.dueDate();
        String goods = blank(child.goodsName()).isEmpty() ? mother.goodsName() : child.goodsName();
        return station(
                WORK, child.code(), mother.code(), child.code(), null, mother,
                goods, blank(child.materialCode()).isEmpty() ? mother.materialCode() : child.materialCode(),
                child.quantity() == null ? mother.quantity() : child.quantity(),
                blank(child.quantityUnit()).isEmpty() ? mother.quantityUnit() : child.quantityUnit(),
                child.weight() == null ? mother.weight() : child.weight(),
                due, WorkshopProcesses.split(child.processes()), submitted, checks, heads, operators, handoffTimes,
                shipped, finished, logistics, mother.courierNo()
        );
    }

    private OrderWorkspaceRow roundRow(
            MotherOrderRecord mother,
            ExecutionRound round,
            Map<String, Set<String>> submitted,
            Map<String, QualityRequirementCheck> checks,
            Map<String, ProcessProgressHead> heads,
            Map<String, String> operators,
            Map<String, String> handoffTimes,
            Set<String> shipped,
            Map<String, BigDecimal> finished,
            Map<String, Set<String>> logistics,
            Set<String> visibleCodes
    ) {
        String source = blank(round.sourceWorkOrderCode());
        boolean knownSource = source.isEmpty() || visibleCodes.contains(source) || mother.code().equals(source);
        List<String> steps = blank(round.processName()).isEmpty()
                ? WorkshopProcesses.DEFAULT_ORDER
                : List.of(round.processName());
        OrderWorkspaceRow row = station(
                WORK, round.roundWorkOrderCode(), mother.code(), source.isEmpty() ? null : source, round.roundWorkOrderCode(),
                mother, mother.goodsName(), mother.materialCode(), mother.quantity(), mother.quantityUnit(), mother.weight(),
                mother.dueDate(), steps, submitted, checks, heads, operators, handoffTimes, shipped, finished, logistics, mother.courierNo()
        );
        if (knownSource) {
            return row;
        }
        return copyFacet(row, "UNKNOWN", "执行轮次没有对应到可见的原单，不能当作原单提交");
    }

    private OrderWorkspaceRow station(
            String objectType,
            String code,
            String motherCode,
            String workOrderCode,
            String roundCode,
            MotherOrderRecord mother,
            String goodsName,
            String materialCode,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal weight,
            String dueDate,
            List<String> processes,
            Map<String, Set<String>> submitted,
            Map<String, QualityRequirementCheck> checks,
            Map<String, ProcessProgressHead> heads,
            Map<String, String> operators,
            Map<String, String> handoffTimes,
            Set<String> shipped,
            Map<String, BigDecimal> finished,
            Map<String, Set<String>> logistics,
            String courierNo
    ) {
        List<String> steps = processes == null || processes.isEmpty() ? WorkshopProcesses.DEFAULT_ORDER : processes;
        Set<String> done = submitted.getOrDefault(code, Set.of());
        String current = steps.get(steps.size() - 1);
        String status = WAITING_QUALITY;
        for (String step : steps) {
            if (!done.contains(step)) {
                current = step;
                status = done.isEmpty() ? WAITING_START : IN_PROCESS;
                break;
            }
        }
        QualityRequirementCheck check = checks.get(code);
        String outcome = check == null ? "未质检" : blank(check.outcome());
        if (check != null) {
            status = !blank(check.reworkOrderCode()).isEmpty() ? REWORK : QUALITY_DONE;
        }
        ProcessProgressHead head = heads.get(code + "|" + current);
        Integer percent = head != null && PERCENTS.contains(head.percent()) ? head.percent() : null;
        if (WAITING_START.equals(status) && percent != null && percent > 0) {
            status = IN_PROCESS;
        }
        Integer version = head == null ? null : head.version();
        boolean delivered = done.contains(current);
        String operator = head != null && !blank(head.operator()).isEmpty()
                ? blank(head.operator())
                : emptyToNull(operators.get(code + "|" + current));
        String updated = head != null && !blank(head.updatedAt()).isEmpty()
                ? blank(head.updatedAt())
                : emptyToNull(handoffTimes.get(code + "|" + current));
        return build(
                objectType, code, motherCode, workOrderCode, roundCode, current, mother,
                goodsName, materialCode, quantity, quantityUnit, weight, dueDate, status, outcome,
                percent, version, delivered, operator, updated, shipped, finished, logistics, courierNo
        );
    }

    private OrderWorkspaceRow build(
            String objectType,
            String code,
            String motherCode,
            String workOrderCode,
            String roundCode,
            String process,
            MotherOrderRecord mother,
            String goodsName,
            String materialCode,
            BigDecimal quantity,
            String quantityUnit,
            BigDecimal weight,
            String dueDate,
            String status,
            String outcome,
            Integer percent,
            Integer version,
            Boolean delivered,
            String operator,
            String updatedAt,
            Set<String> shipped,
            Map<String, BigDecimal> finished,
            Map<String, Set<String>> logistics,
            String courierNo
    ) {
        String key = orderKey(code, motherCode);
        Facet facet = facetOf(status, outcome, shipped.contains(key), finished.get(key));
        String rowKey = objectType + "|" + code + "|" + blank(process) + "|" + blank(roundCode);
        return new OrderWorkspaceRow(
                rowKey,
                objectType,
                code,
                motherCode,
                emptyToNull(workOrderCode),
                emptyToNull(roundCode),
                emptyToNull(process),
                mother.customerId(),
                emptyToNull(mother.customerName()),
                emptyToNull(goodsName),
                emptyToNull(materialCode),
                null,
                decimal(quantity),
                emptyToNull(quantityUnit),
                decimal(weight),
                null,
                emptyToNull(dueDate),
                status,
                facet.name,
                facet.note,
                percent,
                version,
                delivered,
                emptyToNull(operator),
                emptyToNull(updatedAt),
                emptyToNull(mother.operator())
        );
    }

    private Facet facetOf(String status, String outcome, boolean shipped, BigDecimal finishedQuantity) {
        boolean releasable = finishedQuantity != null && finishedQuantity.signum() > 0
                && (QualityVerdicts.PASS.equals(outcome) || QualityVerdicts.CONCESSION.equals(outcome));
        if (releasable) {
            return new Facet("READY_TO_SHIP", null);
        }
        if (shipped) {
            return new Facet("SHIPPED", null);
        }
        return switch (status) {
            case WAITING_SPLIT -> new Facet("UNSPLIT", null);
            case WAITING_START -> new Facet("WAITING_START", null);
            case IN_PROCESS -> new Facet("IN_PROCESS", null);
            case WAITING_QUALITY -> new Facet("WAITING_QUALITY", null);
            case REWORK -> new Facet("REWORK", null);
            case QUALITY_DONE -> new Facet("UNKNOWN", "已质检，尚未确认放行和可发库存");
            default -> new Facet("UNKNOWN", "来源不足，不能判断下一步");
        };
    }

    /** 工序尚未全量完成时，下道岗位也能看到上道已经交出的可办数量。 */
    private OrderWorkspaceRow batchRow(OrderWorkspaceRow row, OrderWorkspaceQuery query, List<String> allowed,
            List<com.zhubao.erp.business.domain.ProcessBatchHandoff> batches) {
        if (batches.isEmpty() || (!WAITING_START.equals(row.businessStatus()) && !IN_PROCESS.equals(row.businessStatus()))) return row;
        String process = row.currentProcess();
        if (OrderWorkspaceQuery.PRODUCTION.equals(query.view())) {
            var child = documents.findChildWorkOrderByCode(row.code()).orElse(null);
            List<String> steps = child == null ? WorkshopProcesses.DEFAULT_ORDER : WorkshopProcesses.split(child.processes());
            if (row.executionRoundCode() != null) steps = List.of(row.currentProcess());
            else steps = OrderRequirementLookup.route(OrderRequirementLookup.current(requirements, row.motherOrderCode(), child == null ? "" : child.code()).orElse(null), steps);
            var completed = documents.listProcessStationHandoffs(row.code()).stream().map(ProcessStationHandoff::processName).collect(java.util.stream.Collectors.toSet());
            List<String> bound = WorkshopProcesses.bind(allowed);
            BigDecimal total = new BigDecimal(row.quantity());
            for (int index = 0; index < steps.size(); index++) {
                String step = steps.get(index);
                if ((!bound.isEmpty() && !bound.contains(step)) || completed.contains(step)) continue;
                BigDecimal delivered = batches.stream().filter(batch -> batch.processName().equals(step)).map(com.zhubao.erp.business.domain.ProcessBatchHandoff::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                String previous = index == 0 ? "" : steps.get(index - 1);
                BigDecimal received = previous.isEmpty() || completed.contains(previous) ? total : batches.stream().filter(batch -> batch.processName().equals(previous)).map(com.zhubao.erp.business.domain.ProcessBatchHandoff::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (received.compareTo(delivered) > 0 || (index == steps.size() - 1 && delivered.compareTo(total) == 0)) { process = step; break; }
            }
        }
        boolean same = java.util.Objects.equals(process, row.currentProcess());
        return new OrderWorkspaceRow(row.objectType() + "|" + row.code() + "|" + process + "|" + blank(row.executionRoundCode()),
                row.objectType(), row.code(), row.motherOrderCode(), row.workOrderCode(), row.executionRoundCode(), process,
                row.customerId(), row.customerName(), row.goodsName(), row.materialCode(), row.materialName(), row.quantity(), row.quantityUnit(),
                row.weight(), row.weightUnit(), row.dueDate(), IN_PROCESS, "IN_PROCESS", row.facetNote(), same ? row.processPercent() : null,
                same ? row.processProgressVersion() : null, false, same ? row.operatorName() : null, same ? row.updatedAt() : null, row.createdBy());
    }

    private boolean matchesView(String view, OrderWorkspaceRow row, List<String> allowedProcesses) {
        if (OrderWorkspaceQuery.PRODUCTION.equals(view)) {
            if (!WAITING_START.equals(row.businessStatus()) && !IN_PROCESS.equals(row.businessStatus())) {
                return false;
            }
            List<String> bound = WorkshopProcesses.bind(allowedProcesses);
            return bound.isEmpty() || bound.contains(blank(row.currentProcess()));
        }
        if (OrderWorkspaceQuery.QUALITY.equals(view)) {
            return WAITING_QUALITY.equals(row.businessStatus())
                    || REWORK.equals(row.businessStatus())
                    || QUALITY_DONE.equals(row.businessStatus());
        }
        return true;
    }

    private boolean matchesQuery(String query, OrderWorkspaceRow row, Loaded loaded) {
        if (query.isEmpty()) {
            return true;
        }
        if (query.equals(row.code()) || query.equals(row.motherOrderCode())
                || query.equals(blank(row.workOrderCode())) || query.equals(blank(row.executionRoundCode()))) {
            return true;
        }
        if (query.equals(blank(loaded.courierByCode().get(row.code())))
                || query.equals(blank(loaded.courierByCode().get(row.motherOrderCode())))) {
            return true;
        }
        Set<String> logisticsTargets = loaded.logisticsByNumber().getOrDefault(query, Set.of());
        if (logisticsTargets.contains(row.code()) || logisticsTargets.contains(row.motherOrderCode())) {
            return true;
        }
        String goods = blank(row.goodsName());
        String customer = blank(row.customerName());
        return goods.contains(query) || customer.contains(query);
    }

    private boolean matchesDue(LocalDate dueBefore, String dueDate) {
        if (dueBefore == null) {
            return true;
        }
        String text = blank(dueDate);
        if (text.length() < 10) {
            return false;
        }
        try {
            return !LocalDate.parse(text.substring(0, 10)).isAfter(dueBefore);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Integer> facetCounts(List<OrderWorkspaceRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String facet : OrderWorkspaceQuery.FACETS) {
            counts.put(facet, 0);
        }
        counts.put(OrderWorkspaceQuery.ALL, rows.size());
        for (OrderWorkspaceRow row : rows) {
            counts.merge(row.facet(), 1, Integer::sum);
        }
        return counts;
    }

    private List<String> availableViews(List<String> menuCodes) {
        Set<String> menus = new LinkedHashSet<>(menuCodes);
        List<String> views = new ArrayList<>();
        if (menus.contains("inbound") || menus.contains("progress") || menus.contains("production")
                || menus.contains("quality") || menus.contains("inventory")) {
            views.add(OrderWorkspaceQuery.ORDERS);
        }
        if (menus.contains("production") || menus.contains("progress")) {
            views.add(OrderWorkspaceQuery.PRODUCTION);
        }
        if (menus.contains("quality")) {
            views.add(OrderWorkspaceQuery.QUALITY);
        }
        return List.copyOf(views);
    }

    private boolean withinScope(OperatorContext operator, MotherOrderRecord mother) {
        return DocumentVisibility.canSeeAnyWorkplace(operator, mother.customerId(), mother.customerName(),
                List.of(blank(mother.operator()), blank(mother.receiverName()), blank(mother.unpackSigner())),
                accounts == null ? List.of() : accounts.findAll());
    }

    private OrderWorkspaceRow copyFacet(OrderWorkspaceRow row, String facet, String note) {
        return new OrderWorkspaceRow(
                row.rowKey(), row.objectType(), row.code(), row.motherOrderCode(), row.workOrderCode(),
                row.executionRoundCode(), row.currentProcess(), row.customerId(), row.customerName(), row.goodsName(),
                row.materialCode(), row.materialName(), row.quantity(), row.quantityUnit(), row.weight(), row.weightUnit(),
                row.dueDate(), row.businessStatus(), facet, note, row.processPercent(), row.processProgressVersion(),
                row.processFormallyDelivered(), row.operatorName(), row.updatedAt(), row.createdBy()
        );
    }

    private static String orderKey(String code, String motherCode) {
        String text = blank(code);
        return text.isEmpty() ? blank(motherCode) : text;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String emptyToNull(String value) {
        String text = blank(value);
        return text.isEmpty() ? null : text;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static final Comparator<OrderWorkspaceRow> ROW_ORDER = Comparator
            .comparing((OrderWorkspaceRow row) -> blank(row.dueDate()).isEmpty() ? "9999-99-99" : blank(row.dueDate()))
            .thenComparing(row -> blank(row.motherOrderCode()))
            .thenComparing(row -> blank(row.objectType()))
            .thenComparing(row -> blank(row.code()))
            .thenComparing(OrderWorkspaceRow::rowKey);

    private record Facet(String name, String note) {
    }

    private record Loaded(
            List<OrderWorkspaceRow> rows,
            Map<String, String> courierByCode,
            Map<String, Set<String>> logisticsByNumber
    ) {
    }
}
