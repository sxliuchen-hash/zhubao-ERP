package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalAccess;
import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.domain.BusinessReviewData.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 正式工作台的只读投影。每次读取只在本次请求内装入快照并建索引，请求结束后即丢弃，不跨请求缓存。
 * 库存页仍返回授权范围内的完整流水，用来计算前后余额；因此不做分页。客户范围明确时台账按客户或母单缩小读取。
 */
final class FormalBusinessWorkspaceQuery {
    private final BusinessDocumentRepository documents;
    private final ProgressBoardService progress;
    private final StockLedgerService stocks;
    private final ApprovalService approvals;
    private final ApprovalAccess approvalAccess = new ApprovalAccess();
    private final AuthUserAccountRepository accounts;

    FormalBusinessWorkspaceQuery(BusinessDocumentRepository documents, ProgressBoardService progress,
                                 StockLedgerService stocks, ApprovalService approvals) {
        this(documents, progress, stocks, approvals, null);
    }

    FormalBusinessWorkspaceQuery(BusinessDocumentRepository documents, ProgressBoardService progress,
                                 StockLedgerService stocks, ApprovalService approvals,
                                 AuthUserAccountRepository accounts) {
        this.documents = documents;
        this.progress = progress;
        this.stocks = stocks;
        this.approvals = approvals;
        this.accounts = accounts;
    }

    BusinessWorkspaceSummary summary(OperatorContext operator) {
        Snapshot snapshot = load(operator, View.SUMMARY);
        InboundWorkspaceData inbound = inbound(operator, snapshot);
        ProductionWorkspaceData production = production(operator, snapshot);
        QualityWorkspaceData quality = quality(operator, snapshot);
        InventoryWorkspaceData inventory = inventory(operator, snapshot);
        MobileWorkspaceData mobile = mobile(operator, snapshot, production, quality);
        return new BusinessWorkspaceSummary(inbound.orders().size(), production.workOrders().size(),
                quality.cases().size(), inventory.ledgerEntries().size(), mobile.tasks().size(), "LIVE");
    }

    InboundWorkspaceData inbound(OperatorContext operator) {
        return inbound(operator, load(operator, View.INBOUND));
    }

    ProductionWorkspaceData production(OperatorContext operator) {
        return production(operator, load(operator, View.PRODUCTION));
    }

    QualityWorkspaceData quality(OperatorContext operator) {
        return quality(operator, load(operator, View.QUALITY));
    }

    InventoryWorkspaceData inventory(OperatorContext operator) {
        return inventory(operator, load(operator, View.INVENTORY));
    }

    MobileWorkspaceData mobile(OperatorContext operator) {
        Snapshot snapshot = load(operator, View.MOBILE);
        return mobile(operator, snapshot, production(operator, snapshot), quality(operator, snapshot));
    }

    private InboundWorkspaceData inbound(OperatorContext operator, Snapshot snapshot) {
        List<InboundOrder> orders = snapshot.mothers.stream().filter(mother -> visible(mother, operator, "inbound")).map(mother -> {
            String goods = text(mother.goodsName());
            return new InboundOrder(mother.code(), customer(mother), goods.isEmpty() ? mother.materialCode() : goods,
                    text(mother.inboundDate()), text(mother.expressCompany()), text(mother.courierNo()),
                    text(mother.senderName()), text(mother.receiverName()), text(mother.unpackSigner()), goods,
                    number(mother.quantity()).intValue(), weight(mother.weight()), "已入库",
                    text(mother.evidenceCode()).isEmpty() ? "未关联" : "已关联", mother.customerOwned() ? "客户所有" : "公司所有",
                    "加工要求：" + text(mother.processingRequirement()) + "；期限：" + text(mother.dueDate()),
                    context(mother.code(), "", Set.of(StockStatus.RAW_WAITING), snapshot));
        }).toList();
        return new InboundWorkspaceData(List.of(), orders, List.of(), "LIVE");
    }

    private ProductionWorkspaceData production(OperatorContext operator, Snapshot snapshot) {
        List<WorkOrder> orders = new ArrayList<>();
        for (ProgressRow row : rows(operator, "production", snapshot)) {
            if ("inbound".equals(row.openTab())) continue;
            MotherOrderRecord mother = snapshot.mother(row.motherOrderCode());
            ChildWorkOrderRecord child = snapshot.child(row.workOrderCode());
            List<ProcessStationHandoff> handoffs = snapshot.handoffsOf(row.workOrderCode());
            String assignee = handoffs.isEmpty() ? "" : text(handoffs.get(handoffs.size() - 1).operator());
            BigDecimal quantity = child == null ? mother.quantity() : child.quantity();
            List<String> processes = child == null ? WorkshopProcesses.DEFAULT_ORDER : WorkshopProcesses.split(child.processes());
            int completed = (int) handoffs.stream().map(ProcessStationHandoff::processName).distinct().count();
            int percent = processes.isEmpty() ? 0 : Math.min(100, completed * 100 / processes.size());
            orders.add(new WorkOrder(row.workOrderCode(), row.customerName(), child == null ? mother.materialCode() : child.materialCode(),
                    row.currentProcess(), "", "", "", "", "", "", row.status(), "", assignee,
                    number(quantity).intValue(), percent == 100 ? number(quantity).intValue() : 0, row.status(), percent,
                    context(mother.code(), row.workOrderCode(), Set.of(StockStatus.RAW_WAITING, StockStatus.WIP, StockStatus.REWORK), snapshot)));
        }
        // 较早通用报工/返工命令只写台账，仍须展示这些实际库存，不能因没有现场进度记录而隐去。
        for (StockBalance balance : snapshot.balances) {
            StockDimension dimension = balance.dimension();
            String code = text(dimension.workOrderCode());
            if (code.isEmpty() || !positive(balance) || !Set.of(StockStatus.WIP, StockStatus.REWORK).contains(dimension.stockStatus())
                    || !visibleOrder(code, operator, "production", snapshot) || orders.stream().anyMatch(order -> code.equals(order.code()))) continue;
            StockLedgerEntry latest = snapshot.latest(dimension);
            MotherOrderRecord mother = snapshot.mother(dimension.motherOrderCode());
            orders.add(new WorkOrder(code, mother == null ? "客户#" + dimension.customerId() : customer(mother), dimension.materialCode(),
                    remarkField(latest, "工序"), "", "", "", "", "", "", status(dimension.stockStatus()), "", remarkField(latest, "操作人"),
                    balance.quantity().intValue(), 0, status(dimension.stockStatus()), 0,
                    context(dimension.motherOrderCode(), code, Set.of(StockStatus.WIP, StockStatus.REWORK), snapshot)));
        }
        List<ProductionHandoff> handoffs = snapshot.handoffs.stream()
                .filter(handoff -> visibleOrder(handoff.orderCode(), operator, "production", snapshot))
                .map(handoff -> new ProductionHandoff(handoff.ledgerCode(), handoff.processName(), "已交付", "", "", "", "已交付", "green")).toList();
        return new ProductionWorkspaceData(List.of(), List.copyOf(orders), handoffs, "LIVE");
    }

    private QualityWorkspaceData quality(OperatorContext operator, Snapshot snapshot) {
        List<QualityCase> cases = new ArrayList<>();
        for (ProgressRow row : rows(operator, "quality", snapshot)) {
            if (!"quality".equals(row.openTab())) continue;
            QualityRequirementCheck check = snapshot.quality(row.workOrderCode());
            cases.add(new QualityCase(check == null ? row.workOrderCode() : check.qualityCaseCode(), row.workOrderCode(),
                    row.currentProcess(), check == null ? "" : text(check.inspector()), defects(check), check == null ? "待质检" : check.outcome(), row.status(),
                    context(row.motherOrderCode(), row.workOrderCode(), Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED), snapshot)));
        }
        for (StockBalance balance : snapshot.balances) {
            StockDimension dimension = balance.dimension();
            String code = text(dimension.workOrderCode()).isEmpty() ? dimension.motherOrderCode() : dimension.workOrderCode();
            if (!positive(balance) || !Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED).contains(dimension.stockStatus())
                    || !visibleOrder(code, operator, "quality", snapshot) || cases.stream().anyMatch(item -> code.equals(item.workOrder()))) continue;
            QualityRequirementCheck check = snapshot.quality(code);
            StockLedgerEntry latest = snapshot.latest(dimension);
            String caseCode = check != null ? check.qualityCaseCode()
                    : latest != null && Set.of("quality_freeze", "quality_release").contains(latest.documentType()) ? latest.documentCode()
                    : remarkField(latest, "质检单").isEmpty() ? code : remarkField(latest, "质检单");
            cases.add(new QualityCase(caseCode, code, remarkField(latest, "工序"), check == null ? remarkField(latest, "质检员") : check.inspector(),
                    check == null ? remarkField(latest, "缺陷") : defects(check), check == null ? status(dimension.stockStatus()) : check.outcome(),
                    status(dimension.stockStatus()), context(dimension.motherOrderCode(), code,
                    Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED), snapshot)));
        }
        List<LossApproval> losses = snapshot.approvals.stream()
                .filter(task -> "LOSS_APPROVAL".equals(task.scene()) && visibleOrder(task.businessCode(), operator, "loss", snapshot))
                .map(task -> new LossApproval(task.approvalCode(), task.businessCode(), "", "", "", task.evidenceCode(),
                        task.status().name(), task.severity(), contextForOrder(task.businessCode(), snapshot))).toList();
        return new QualityWorkspaceData(List.of(), List.copyOf(cases), losses, "LIVE");
    }

    private InventoryWorkspaceData inventory(OperatorContext operator, Snapshot snapshot) {
        List<InventoryBalance> balances = snapshot.balances.stream()
                .filter(balance -> positive(balance) && processingStock(balance.dimension()) && visible(balance.dimension(), operator, snapshot))
                .map(balance -> {
            StockDimension dimension = balance.dimension();
            MotherOrderRecord mother = snapshot.mother(dimension.motherOrderCode());
            String unit = mother == null || text(mother.quantityUnit()).isEmpty() ? "" : mother.quantityUnit();
            String frozen = dimension.stockStatus() == StockStatus.FROZEN ? plain(balance.quantity()) : "0";
            return new InventoryBalance(ownerLabel(dimension, mother), batchLabel(dimension),
                    dimension.materialCode(), dimension.warehouseCode(), status(dimension.stockStatus()), plain(balance.quantity()),
                    weight(balance.weight()), frozen, context(balance), unit);
        }).toList();
        List<InventoryLedgerEntry> entries = new ArrayList<>();
        List<TraceEvent> timeline = new ArrayList<>();
        Map<StockDimension, BigDecimal[]> running = new LinkedHashMap<>();
        for (StockLedgerEntry entry : snapshot.ledgers) {
            StockDimension dimension = entry.to() == null ? entry.from() : entry.to();
            BigDecimal[] before = running.getOrDefault(dimension, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}).clone();
            apply(running, entry.from(), entry.quantity().negate(), entry.weight().negate());
            apply(running, entry.to(), entry.quantity(), entry.weight());
            if (!visible(dimension, operator, snapshot)) continue;
            BigDecimal[] after = running.getOrDefault(dimension, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            String actor = ledgerOperator(entry, snapshot);
            entries.add(new InventoryLedgerEntry(entry.bizTime().toString(), entry.code(), movementLabel(entry.documentType()), balanceText(before), balanceText(after), entry.documentCode(), actor));
            timeline.add(new TraceEvent(entry.bizTime().toString(), entry.documentType(), entry.documentCode(), actor, ""));
        }
        return new InventoryWorkspaceData(balances, List.copyOf(entries), List.copyOf(timeline), List.of(), "LIVE");
    }

    private MobileWorkspaceData mobile(OperatorContext operator, Snapshot snapshot, ProductionWorkspaceData production,
                                       QualityWorkspaceData qualityData) {
        List<MobileTask> tasks = new ArrayList<>(rows(operator, "mobile", snapshot).stream().filter(row -> !ProgressBoardService.QUALITY_DONE.equals(row.status()))
                .filter(row -> !"inbound".equals(row.openTab()) || context(row.motherOrderCode(), "", Set.of(StockStatus.RAW_WAITING), snapshot) != null)
                .map(row -> new MobileTask(row.workOrderCode(), row.currentProcess(),
                        "inbound".equals(row.openTab()) ? "确认拆单方式" : "quality".equals(row.openTab()) ? "质检核对" : "岗位报工",
                        "按正式单据处理", row.status())).toList());
        for (WorkOrder order : production.workOrders()) {
            if (order.operationContext() == null || tasks.stream().anyMatch(task -> task.code().equals(order.code()))) continue;
            tasks.add(new MobileTask(order.code(), order.process(), "岗位报工", "按正式单据处理", order.status()));
        }
        for (QualityCase qualityCase : qualityData.cases()) {
            if (qualityCase.operationContext() == null || "FINISHED".equals(qualityCase.operationContext().stockStatus())
                    || tasks.stream().anyMatch(task -> task.code().equals(qualityCase.workOrder()))) continue;
            tasks.add(new MobileTask(qualityCase.workOrder(), qualityCase.process(), "质检核对", "按正式单据处理", qualityCase.status()));
        }
        return new MobileWorkspaceData(List.copyOf(tasks), List.of(), List.of(), "LIVE");
    }

    private Snapshot load(OperatorContext operator, View view) {
        EnumSet<Part> parts = partsFor(view);
        List<MotherOrderRecord> mothers = parts.contains(Part.MOTHERS) && documents != null ? documents.listMotherOrders() : List.of();
        List<ChildWorkOrderRecord> children = parts.contains(Part.CHILDREN) && documents != null ? documents.listAllChildWorkOrders() : List.of();
        List<ProcessStationHandoff> handoffs = parts.contains(Part.HANDOFFS) && documents != null ? documents.listAllProcessStationHandoffs() : List.of();
        List<QualityRequirementCheck> checks = parts.contains(Part.CHECKS) && documents != null ? documents.listAllQualityRequirementChecks() : List.of();
        Map<String, MotherSplitState> splits = new LinkedHashMap<>();
        if (parts.contains(Part.SPLITS) && documents != null) {
            for (MotherSplitState state : documents.listAllMotherSplitStates()) {
                splits.putIfAbsent(state.motherOrderCode(), state);
            }
        }
        List<StockBalance> balances = parts.contains(Part.BALANCES) && stocks != null ? stocks.balances() : List.of();
        List<StockLedgerEntry> ledgers = parts.contains(Part.LEDGERS) ? loadLedgers(operator, mothers) : List.of();
        List<ApprovalTask> approvalTasks = visibleApprovals(operator, parts);
        List<ProgressRow> progressBase = List.of();
        if (parts.contains(Part.PROGRESS) && progress != null && documents != null) {
            progressBase = progress.listFrom(operator, "", "", "", "", children, handoffs, checks, mothers, splits);
        }
        return new Snapshot(mothers, children, handoffs, checks, balances, ledgers, approvalTasks, progressBase);
    }

    private List<ApprovalTask> visibleApprovals(OperatorContext operator, EnumSet<Part> parts) {
        if (!parts.contains(Part.APPROVALS) || approvals == null || operator == null || operator.role() == null
                || text(operator.operator()).isEmpty()) {
            return List.of();
        }
        ApprovalActor actor = new ApprovalActor(operator.operator(), "", operator.role());
        return approvals.findAll().stream().filter(task -> approvalAccess.canRead(actor, task)).toList();
    }

    private List<StockLedgerEntry> loadLedgers(OperatorContext operator, List<MotherOrderRecord> mothers) {
        if (stocks == null) {
            return List.of();
        }
        // 全部数据一次读取。名单为空只是没有客户过滤条件，读取后仍按单据归属收敛，不把空名单当成全部客户。
        if (operator.dataScope() == DataScope.ALL || operator.customerScope().isEmpty()) {
            return stocks.ledgers();
        }
        if (operator.customerScope().isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : operator.customerScope()) {
            String normalized = text(token);
            if (normalized.isEmpty()) {
                continue;
            }
            tokens.add(normalized);
            Long id = canonicalCustomerId(normalized);
            if (id != null) {
                ids.add(id);
            }
        }
        Set<String> motherCodes = new LinkedHashSet<>();
        for (MotherOrderRecord mother : mothers) {
            if (tokens.contains(text(mother.customerName()))
                    || mother.customerId() != null && tokens.contains(mother.customerId().toString())) {
                motherCodes.add(mother.code());
            }
        }
        if (ids.isEmpty() && motherCodes.isEmpty()) {
            return List.of();
        }
        return stocks.ledgersForCustomers(ids, motherCodes);
    }

    private static Long canonicalCustomerId(String token) {
        try {
            long value = Long.parseLong(token);
            return Long.toString(value).equals(token) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static EnumSet<Part> partsFor(View view) {
        return switch (view) {
            case INBOUND -> EnumSet.of(Part.MOTHERS, Part.BALANCES);
            case PRODUCTION -> EnumSet.of(Part.MOTHERS, Part.CHILDREN, Part.HANDOFFS, Part.CHECKS, Part.SPLITS, Part.BALANCES, Part.LEDGERS, Part.PROGRESS);
            case QUALITY -> EnumSet.of(Part.MOTHERS, Part.CHILDREN, Part.HANDOFFS, Part.CHECKS, Part.SPLITS, Part.BALANCES, Part.LEDGERS, Part.PROGRESS, Part.APPROVALS);
            case INVENTORY -> EnumSet.of(Part.MOTHERS, Part.CHILDREN, Part.HANDOFFS, Part.CHECKS, Part.BALANCES, Part.LEDGERS);
            case MOBILE, SUMMARY -> EnumSet.allOf(Part.class);
        };
    }

    private List<ProgressRow> rows(OperatorContext operator, String area, Snapshot snapshot) {
        if (progress == null || documents == null) {
            return List.of();
        }
        return snapshot.progressBase.stream()
                .filter(row -> visibleOrder(row.workOrderCode(), operator, "mobile".equals(area) ? row.openTab() : area, snapshot))
                .toList();
    }

    private boolean visibleOrder(String code, OperatorContext operator, String area, Snapshot snapshot) {
        if (documents == null) return false;
        ChildWorkOrderRecord child = snapshot.child(code);
        MotherOrderRecord mother = snapshot.mother(child == null ? code : child.motherOrderCode());
        if (child != null && mother != null
                && CustomerScopeRules.identityConflicts(child.customerId(), mother.customerId())) {
            return false;
        }
        if (mother == null) {
            String onlyMother = null;
            Long onlyCustomer = null;
            boolean ambiguous = false;
            for (StockBalance balance : snapshot.balances) {
                StockDimension dimension = balance.dimension();
                if (!code.equals(dimension.workOrderCode())) {
                    continue;
                }
                if (onlyMother == null) {
                    onlyMother = dimension.motherOrderCode();
                    onlyCustomer = dimension.customerId();
                } else if (!onlyMother.equals(dimension.motherOrderCode())
                        || CustomerScopeRules.identityConflicts(onlyCustomer, dimension.customerId())) {
                    ambiguous = true;
                    break;
                }
            }
            if (ambiguous) return false;
            if (onlyMother != null) {
                mother = snapshot.mother(onlyMother);
                if (mother != null && CustomerScopeRules.identityConflicts(onlyCustomer, mother.customerId())) return false;
            }
        }
        List<String> participants = new ArrayList<>();
        if (mother != null) {
            participants.add(mother.operator());
            participants.add(mother.receiverName());
            participants.add(mother.unpackSigner());
        }
        snapshot.handoffsOf(code).forEach(handoff -> participants.add(handoff.operator()));
        if (snapshot.quality(code) != null) {
            participants.add(snapshot.quality(code).inspector());
        }
        return mother != null && DocumentVisibility.canSee(operator, mother.customerId(), mother.customerName(),
                area, participants, directory());
    }

    private boolean visible(MotherOrderRecord mother, OperatorContext operator, String area) {
        if (mother == null) return false;
        return DocumentVisibility.canSee(operator, mother.customerId(), mother.customerName(), area,
                List.of(text(mother.operator()), text(mother.receiverName()), text(mother.unpackSigner())), directory());
    }

    private boolean visible(StockDimension dimension, OperatorContext operator, Snapshot snapshot) {
        if (dimension == null) return false;
        if (dimension.ownerType() == com.zhubao.erp.inventory.domain.OwnerType.COMPANY && !canReadCompanyStock(operator)) {
            return false;
        }
        MotherOrderRecord mother = snapshot.mother(dimension.motherOrderCode());
        String code = text(dimension.workOrderCode()).isEmpty() ? dimension.motherOrderCode() : dimension.workOrderCode();
        List<String> participants = new ArrayList<>();
        if (mother != null) {
            participants.add(mother.operator());
            participants.add(mother.receiverName());
            participants.add(mother.unpackSigner());
        }
        snapshot.handoffsOf(code).forEach(handoff -> participants.add(handoff.operator()));
        return DocumentVisibility.canSee(operator, dimension.customerId(), mother == null ? "" : mother.customerName(),
                "inventory", participants, directory());
    }

    private List<AuthUserAccount> directory() {
        return accounts == null ? List.of() : accounts.findAll();
    }

    private static String defects(QualityRequirementCheck check) {
        return check == null ? "" : check.lines().stream().filter(line -> QualityVerdicts.needsReason(line.verdict()))
                .map(line -> line.clause() + "：" + line.reason()).reduce((left, right) -> left + "；" + right).orElse("");
    }

    private OperationContext contextForOrder(String code, Snapshot snapshot) {
        ChildWorkOrderRecord child = snapshot.child(code);
        String motherCode = child == null ? firstMotherCode(code, snapshot) : child.motherOrderCode();
        return context(motherCode, code, Set.of(StockStatus.WIP, StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK), snapshot);
    }

    private String firstMotherCode(String code, Snapshot snapshot) {
        String only = null;
        for (StockBalance balance : snapshot.balances) {
            if (!code.equals(balance.dimension().workOrderCode())) continue;
            String candidate = balance.dimension().motherOrderCode();
            if (candidate == null || candidate.isBlank()) continue;
            if (only == null) only = candidate;
            else if (!only.equals(candidate)) return "";
        }
        return only == null ? code : only;
    }

    private OperationContext context(String motherCode, String orderCode, Set<StockStatus> statuses, Snapshot snapshot) {
        List<StockBalance> matches = new ArrayList<>();
        for (StockBalance balance : snapshot.balances) {
            if (!positive(balance) || !Objects.equals(motherCode, balance.dimension().motherOrderCode())) {
                continue;
            }
            if (!matchesWorkOrder(motherCode, orderCode, balance.dimension()) || !statuses.contains(balance.dimension().stockStatus())) {
                continue;
            }
            matches.add(balance);
        }
        // 多仓库或多状态有余额时，不能替使用者猜测来源库存。
        return matches.size() == 1 ? context(matches.get(0)) : null;
    }

    private boolean matchesWorkOrder(String motherCode, String orderCode, StockDimension dimension) {
        String requested = text(orderCode);
        String stored = text(dimension.workOrderCode());
        if (!requested.equals(motherCode)) {
            return requested.equals(stored);
        }
        // 不拆母单在收货阶段尚无工单，首报转在制后以母单号作为真实加工工单。
        if (dimension.stockStatus() == StockStatus.RAW_WAITING) {
            return stored.isEmpty();
        }
        // 保留历史仅以空工单登记的实际库存；与正式工单并存时由唯一余额检查阻断猜测。
        return requested.equals(stored) || stored.isEmpty();
    }

    private OperationContext context(StockBalance balance) {
        StockDimension dimension = balance.dimension();
        return new OperationContext(dimension.customerId(), dimension.ownerCustomerId(), dimension.motherOrderCode(), text(dimension.workOrderCode()),
                dimension.materialCode(), dimension.warehouseCode(), dimension.ownerType().name(), dimension.stockStatus().name(), balance.quantity(), balance.weight());
    }

    private String ledgerOperator(StockLedgerEntry entry, Snapshot snapshot) {
        if (documents == null) return "";
        MotherOrderRecord mother = snapshot.mother(entry.documentCode());
        if (mother != null && Objects.equals(mother.ledgerCode(), entry.code())) return text(mother.operator());
        ProcessStationHandoff handoff = snapshot.firstHandoffByLedger.get(entry.code());
        return handoff == null ? "" : handoff.operator();
    }

    private static String remarkField(StockLedgerEntry entry, String key) {
        if (entry == null) return "";
        // 旧通用命令将工序、缺陷等正式输入保存在带中文字段名的台账摘要中；缺少字段保持空值。
        String prefix = key + "：";
        for (String part : text(entry.remark()).split("；")) {
            if (part.startsWith(prefix)) return part.substring(prefix.length());
        }
        return "";
    }

    private static void apply(Map<StockDimension, BigDecimal[]> balances, StockDimension dimension, BigDecimal quantity, BigDecimal weight) {
        if (dimension == null) return;
        BigDecimal[] current = balances.computeIfAbsent(dimension, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        current[0] = current[0].add(quantity);
        current[1] = current[1].add(weight);
    }
    private static String balanceText(BigDecimal[] balance) { return balance[0].stripTrailingZeros().toPlainString() + " / " + weight(balance[1]); }
    private static boolean positive(StockBalance balance) { return balance.quantity().signum() > 0 || balance.weight().signum() > 0; }
    private static BigDecimal number(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String plain(BigDecimal value) { return number(value).stripTrailingZeros().toPlainString(); }
    private static String weight(BigDecimal value) { return number(value).stripTrailingZeros().toPlainString() + " g"; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String customer(MotherOrderRecord mother) { return text(mother.customerName()).isEmpty() ? "客户#" + mother.customerId() : mother.customerName(); }

    private static boolean processingStock(StockDimension dimension) {
        return !text(dimension.motherOrderCode()).isBlank() || !text(dimension.workOrderCode()).isBlank();
    }

    private static String ownerLabel(StockDimension dimension, MotherOrderRecord mother) {
        if (dimension.ownerType() == com.zhubao.erp.inventory.domain.OwnerType.COMPANY) {
            return "公司自有";
        }
        if (mother != null) {
            return customer(mother);
        }
        return dimension.customerId() == null ? "客户未登记" : "客户#" + dimension.customerId();
    }

    private static String batchLabel(StockDimension dimension) {
        if (!text(dimension.batchCode()).isBlank()) {
            return dimension.batchCode();
        }
        if (!text(dimension.workOrderCode()).isBlank()) {
            return dimension.workOrderCode();
        }
        return dimension.motherOrderCode();
    }

    private static String movementLabel(String documentType) {
        return switch (text(documentType)) {
            case "inbound_confirm" -> "收货入库";
            case "inbound_reversal" -> "收货冲正";
            case "prep_receipt" -> "寄存收货";
            case "prep_issue" -> "备料领用";
            case "prep_return" -> "退回备料";
            case "prep_customer_return" -> "退还客户";
            case "company_receipt" -> "公司料入库";
            case "company_issue" -> "公司料领用";
            case "company_return" -> "公司料退回";
            case "stock_freeze" -> "冻结";
            case "stock_unfreeze" -> "解除冻结";
            case "stock_correction" -> "冲正";
            default -> text(documentType);
        };
    }

    private static boolean canReadCompanyStock(OperatorContext operator) {
        return operator.role() == com.zhubao.erp.security.domain.RoleCode.ADMIN
                || operator.role() == com.zhubao.erp.security.domain.RoleCode.WAREHOUSE_KEEPER
                || operator.role() == com.zhubao.erp.security.domain.RoleCode.FINANCE_STATISTICIAN
                || operator.role() == com.zhubao.erp.security.domain.RoleCode.GENERAL_MANAGER;
    }

    private static String status(StockStatus status) {
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

    private enum View {
        INBOUND, PRODUCTION, QUALITY, INVENTORY, MOBILE, SUMMARY
    }

    private enum Part {
        MOTHERS, CHILDREN, HANDOFFS, CHECKS, SPLITS, BALANCES, LEDGERS, APPROVALS, PROGRESS
    }

    /**
     * 单次请求的只读索引。调用方不得保存到字段上，避免下一请求读到过期数据。
     */
    private static final class Snapshot {
        private final List<MotherOrderRecord> mothers;
        private final Map<String, MotherOrderRecord> mothersByCode;
        private final Map<String, ChildWorkOrderRecord> childrenByCode;
        private final List<ProcessStationHandoff> handoffs;
        private final Map<String, List<ProcessStationHandoff>> handoffsByOrder;
        private final Map<String, ProcessStationHandoff> firstHandoffByLedger;
        private final Map<String, QualityRequirementCheck> qualityByOrder;
        private final List<StockBalance> balances;
        private final List<StockLedgerEntry> ledgers;
        private final Map<StockDimension, StockLedgerEntry> latestByDimension;
        private final List<ApprovalTask> approvals;
        private final List<ProgressRow> progressBase;

        private Snapshot(List<MotherOrderRecord> mothers, List<ChildWorkOrderRecord> children, List<ProcessStationHandoff> handoffs,
                         List<QualityRequirementCheck> checks, List<StockBalance> balances, List<StockLedgerEntry> ledgers,
                         List<ApprovalTask> approvals, List<ProgressRow> progressBase) {
            this.mothers = mothers;
            Map<String, MotherOrderRecord> motherIndex = new HashMap<>();
            for (MotherOrderRecord mother : mothers) {
                motherIndex.putIfAbsent(mother.code(), mother);
            }
            this.mothersByCode = motherIndex;
            Map<String, ChildWorkOrderRecord> childIndex = new HashMap<>();
            for (ChildWorkOrderRecord child : children) {
                childIndex.putIfAbsent(child.code(), child);
            }
            this.childrenByCode = childIndex;
            this.handoffs = handoffs;
            Map<String, List<ProcessStationHandoff>> handoffIndex = new LinkedHashMap<>();
            Map<String, ProcessStationHandoff> ledgerIndex = new HashMap<>();
            for (ProcessStationHandoff handoff : handoffs) {
                handoffIndex.computeIfAbsent(handoff.orderCode(), key -> new ArrayList<>()).add(handoff);
                if (handoff.ledgerCode() != null) {
                    ledgerIndex.putIfAbsent(handoff.ledgerCode(), handoff);
                }
            }
            this.handoffsByOrder = handoffIndex;
            this.firstHandoffByLedger = ledgerIndex;
            Map<String, QualityRequirementCheck> qualityIndex = new HashMap<>();
            for (QualityRequirementCheck check : checks) {
                qualityIndex.putIfAbsent(check.orderCode(), check);
            }
            this.qualityByOrder = qualityIndex;
            this.balances = balances;
            List<StockLedgerEntry> sorted = new ArrayList<>(ledgers);
            sorted.sort(Comparator.comparing(StockLedgerEntry::bizTime).thenComparing(StockLedgerEntry::code));
            Map<StockDimension, StockLedgerEntry> latest = new HashMap<>();
            for (StockLedgerEntry entry : sorted) {
                if (entry.from() != null) {
                    latest.put(entry.from(), entry);
                }
                if (entry.to() != null) {
                    latest.put(entry.to(), entry);
                }
            }
            this.ledgers = List.copyOf(sorted);
            this.latestByDimension = latest;
            this.approvals = approvals;
            this.progressBase = progressBase;
        }

        private MotherOrderRecord mother(String code) {
            return code == null ? null : mothersByCode.get(code);
        }

        private ChildWorkOrderRecord child(String code) {
            return code == null ? null : childrenByCode.get(code);
        }

        private List<ProcessStationHandoff> handoffsOf(String orderCode) {
            return handoffsByOrder.getOrDefault(orderCode, List.of());
        }

        private QualityRequirementCheck quality(String orderCode) {
            return orderCode == null ? null : qualityByOrder.get(orderCode);
        }

        private StockLedgerEntry latest(StockDimension dimension) {
            return dimension == null ? null : latestByDimension.get(dimension);
        }
    }
}
