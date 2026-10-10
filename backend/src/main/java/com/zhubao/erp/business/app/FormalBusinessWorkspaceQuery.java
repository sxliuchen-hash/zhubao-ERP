package com.zhubao.erp.business.app;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.domain.BusinessReviewData.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 正式工作台的只读投影。没有真实记录的指标、证据与离线草稿保持为空。
 */
final class FormalBusinessWorkspaceQuery {
    private final BusinessDocumentRepository documents;
    private final ProgressBoardService progress;
    private final StockLedgerService stocks;
    private final ApprovalService approvals;

    FormalBusinessWorkspaceQuery(BusinessDocumentRepository documents, ProgressBoardService progress,
                                 StockLedgerService stocks, ApprovalService approvals) {
        this.documents = documents;
        this.progress = progress;
        this.stocks = stocks;
        this.approvals = approvals;
    }

    InboundWorkspaceData inbound(OperatorContext operator) {
        List<InboundOrder> orders = mothers().stream().filter(mother -> visible(mother, operator, "inbound")).map(mother -> {
            String goods = text(mother.goodsName());
            return new InboundOrder(mother.code(), customer(mother), goods.isEmpty() ? mother.materialCode() : goods,
                    text(mother.inboundDate()), text(mother.expressCompany()), text(mother.courierNo()),
                    text(mother.senderName()), text(mother.receiverName()), text(mother.unpackSigner()), goods,
                    number(mother.quantity()).intValue(), weight(mother.weight()), "已入库",
                    text(mother.evidenceCode()).isEmpty() ? "未关联" : "已关联", mother.customerOwned() ? "客户所有" : "公司所有",
                    "加工要求：" + text(mother.processingRequirement()) + "；期限：" + text(mother.dueDate()),
                    context(mother.code(), "", Set.of(StockStatus.RAW_WAITING)));
        }).toList();
        return new InboundWorkspaceData(List.of(), orders, List.of(), "LIVE");
    }

    ProductionWorkspaceData production(OperatorContext operator) {
        List<WorkOrder> orders = new ArrayList<>();
        for (ProgressRow row : rows(operator, "production")) {
            if ("inbound".equals(row.openTab())) continue;
            MotherOrderRecord mother = mother(row.motherOrderCode());
            ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(row.workOrderCode()).orElse(null);
            List<ProcessStationHandoff> handoffs = documents.listProcessStationHandoffs(row.workOrderCode());
            String assignee = handoffs.isEmpty() ? "" : text(handoffs.get(handoffs.size() - 1).operator());
            BigDecimal quantity = child == null ? mother.quantity() : child.quantity();
            List<String> processes = child == null ? WorkshopProcesses.DEFAULT_ORDER : WorkshopProcesses.split(child.processes());
            int completed = (int) handoffs.stream().map(ProcessStationHandoff::processName).distinct().count();
            int percent = processes.isEmpty() ? 0 : Math.min(100, completed * 100 / processes.size());
            orders.add(new WorkOrder(row.workOrderCode(), row.customerName(), child == null ? mother.materialCode() : child.materialCode(),
                    row.currentProcess(), "", "", "", "", "", "", row.status(), "", assignee,
                    number(quantity).intValue(), percent == 100 ? number(quantity).intValue() : 0, row.status(), percent,
                    context(mother.code(), row.workOrderCode(), Set.of(StockStatus.RAW_WAITING, StockStatus.WIP, StockStatus.REWORK))));
        }
        // 较早通用报工/返工命令只写台账，仍须展示这些实际库存，不能因没有现场进度记录而隐去。
        for (StockBalance balance : balances()) {
            StockDimension dimension = balance.dimension();
            String code = text(dimension.workOrderCode());
            if (code.isEmpty() || !positive(balance) || !Set.of(StockStatus.WIP, StockStatus.REWORK).contains(dimension.stockStatus())
                    || !visibleOrder(code, operator, "production") || orders.stream().anyMatch(order -> code.equals(order.code()))) continue;
            StockLedgerEntry latest = latestLedger(dimension);
            MotherOrderRecord mother = mother(dimension.motherOrderCode());
            orders.add(new WorkOrder(code, mother == null ? "客户#" + dimension.customerId() : customer(mother), dimension.materialCode(),
                    remarkField(latest, "工序"), "", "", "", "", "", "", status(dimension.stockStatus()), "", remarkField(latest, "操作人"),
                    balance.quantity().intValue(), 0, status(dimension.stockStatus()), 0,
                    context(dimension.motherOrderCode(), code, Set.of(StockStatus.WIP, StockStatus.REWORK))));
        }
        List<ProductionHandoff> handoffs = documents == null ? List.of() : documents.listAllProcessStationHandoffs().stream()
                .filter(handoff -> visibleOrder(handoff.orderCode(), operator, "production"))
                .map(handoff -> new ProductionHandoff(handoff.ledgerCode(), handoff.processName(), "已交付", "", "", "", "已交付", "green")).toList();
        return new ProductionWorkspaceData(List.of(), List.copyOf(orders), handoffs, "LIVE");
    }

    QualityWorkspaceData quality(OperatorContext operator) {
        List<QualityCase> cases = new ArrayList<>();
        for (ProgressRow row : rows(operator, "quality")) {
            if (!"quality".equals(row.openTab())) continue;
            QualityRequirementCheck check = documents.findQualityRequirementCheck(row.workOrderCode()).orElse(null);
            cases.add(new QualityCase(check == null ? row.workOrderCode() : check.qualityCaseCode(), row.workOrderCode(),
                    row.currentProcess(), check == null ? "" : text(check.inspector()), defects(check), check == null ? "待质检" : check.outcome(), row.status(),
                    context(row.motherOrderCode(), row.workOrderCode(), Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED))));
        }
        for (StockBalance balance : balances()) {
            StockDimension dimension = balance.dimension();
            String code = text(dimension.workOrderCode()).isEmpty() ? dimension.motherOrderCode() : dimension.workOrderCode();
            if (!positive(balance) || !Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED).contains(dimension.stockStatus())
                    || !visibleOrder(code, operator, "quality") || cases.stream().anyMatch(item -> code.equals(item.workOrder()))) continue;
            QualityRequirementCheck check = documents == null ? null : documents.findQualityRequirementCheck(code).orElse(null);
            StockLedgerEntry latest = latestLedger(dimension);
            String caseCode = check != null ? check.qualityCaseCode()
                    : latest != null && Set.of("quality_freeze", "quality_release").contains(latest.documentType()) ? latest.documentCode()
                    : remarkField(latest, "质检单").isEmpty() ? code : remarkField(latest, "质检单");
            cases.add(new QualityCase(caseCode, code, remarkField(latest, "工序"), check == null ? remarkField(latest, "质检员") : check.inspector(),
                    check == null ? remarkField(latest, "缺陷") : defects(check), check == null ? status(dimension.stockStatus()) : check.outcome(),
                    status(dimension.stockStatus()), context(dimension.motherOrderCode(), code,
                    Set.of(StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK, StockStatus.FINISHED))));
        }
        List<LossApproval> losses = approvals == null ? List.of() : approvals.findAll().stream()
                .filter(task -> "LOSS_APPROVAL".equals(task.scene()) && visibleOrder(task.businessCode(), operator, "loss"))
                .map(task -> new LossApproval(task.approvalCode(), task.businessCode(), "", "", "", task.evidenceCode(),
                        task.status().name(), task.severity(), contextForOrder(task.businessCode()))).toList();
        return new QualityWorkspaceData(List.of(), List.copyOf(cases), losses, "LIVE");
    }

    InventoryWorkspaceData inventory(OperatorContext operator) {
        List<InventoryBalance> balances = balances().stream().filter(balance -> positive(balance) && visible(balance.dimension(), operator)).map(balance -> {
            StockDimension dimension = balance.dimension();
            MotherOrderRecord mother = mother(dimension.motherOrderCode());
            return new InventoryBalance(mother == null ? "客户#" + dimension.customerId() : customer(mother),
                    text(dimension.workOrderCode()).isEmpty() ? dimension.motherOrderCode() : dimension.workOrderCode(),
                    dimension.materialCode(), dimension.warehouseCode(), status(dimension.stockStatus()), balance.quantity().intValue(),
                    weight(balance.weight()), dimension.stockStatus() == StockStatus.FROZEN ? "已冻结" : "未冻结", context(balance));
        }).toList();
        List<InventoryLedgerEntry> entries = new ArrayList<>();
        List<TraceEvent> timeline = new ArrayList<>();
        Map<StockDimension, BigDecimal[]> running = new LinkedHashMap<>();
        List<StockLedgerEntry> ledger = stocks == null ? List.of() : stocks.ledgers().stream()
                .sorted(Comparator.comparing(StockLedgerEntry::bizTime).thenComparing(StockLedgerEntry::code)).toList();
        for (StockLedgerEntry entry : ledger) {
            StockDimension dimension = entry.to() == null ? entry.from() : entry.to();
            BigDecimal[] before = running.getOrDefault(dimension, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}).clone();
            apply(running, entry.from(), entry.quantity().negate(), entry.weight().negate());
            apply(running, entry.to(), entry.quantity(), entry.weight());
            if (!visible(dimension, operator)) continue;
            BigDecimal[] after = running.getOrDefault(dimension, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            String actor = ledgerOperator(entry);
            entries.add(new InventoryLedgerEntry(entry.bizTime().toString(), entry.code(), entry.documentType(), balanceText(before), balanceText(after), entry.documentCode(), actor));
            timeline.add(new TraceEvent(entry.bizTime().toString(), entry.documentType(), entry.documentCode(), actor, ""));
        }
        return new InventoryWorkspaceData(balances, List.copyOf(entries), List.copyOf(timeline), List.of(), "LIVE");
    }

    MobileWorkspaceData mobile(OperatorContext operator) {
        List<MobileTask> tasks = new ArrayList<>(rows(operator, "mobile").stream().filter(row -> !ProgressBoardService.QUALITY_DONE.equals(row.status()))
                .filter(row -> !"inbound".equals(row.openTab()) || context(row.motherOrderCode(), "", Set.of(StockStatus.RAW_WAITING)) != null)
                .map(row -> new MobileTask(row.workOrderCode(), row.currentProcess(),
                        "inbound".equals(row.openTab()) ? "确认拆单方式" : "quality".equals(row.openTab()) ? "质检核对" : "岗位报工",
                        "按正式单据处理", row.status())).toList());
        for (WorkOrder order : production(operator).workOrders()) {
            if (order.operationContext() == null || tasks.stream().anyMatch(task -> task.code().equals(order.code()))) continue;
            tasks.add(new MobileTask(order.code(), order.process(), "岗位报工", "按正式单据处理", order.status()));
        }
        for (QualityCase quality : quality(operator).cases()) {
            if (quality.operationContext() == null || "FINISHED".equals(quality.operationContext().stockStatus())
                    || tasks.stream().anyMatch(task -> task.code().equals(quality.workOrder()))) continue;
            tasks.add(new MobileTask(quality.workOrder(), quality.process(), "质检核对", "按正式单据处理", quality.status()));
        }
        return new MobileWorkspaceData(List.copyOf(tasks), List.of(), List.of(), "LIVE");
    }

    private List<MotherOrderRecord> mothers() { return documents == null ? List.of() : documents.listMotherOrders(); }
    private MotherOrderRecord mother(String code) { return documents == null ? null : documents.findMotherOrderByCode(code).orElse(null); }
    private List<StockBalance> balances() { return stocks == null ? List.of() : stocks.balances(); }
    private List<ProgressRow> rows(OperatorContext operator, String area) {
        return progress == null || documents == null ? List.of() : progress.list(operator, "", "", "", "").stream()
                .filter(row -> visibleOrder(row.workOrderCode(), operator, "mobile".equals(area) ? row.openTab() : area)).toList();
    }

    private boolean visibleOrder(String code, OperatorContext operator, String area) {
        if (documents == null) return false;
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(code).orElse(null);
        MotherOrderRecord mother = mother(child == null ? code : child.motherOrderCode());
        if (mother == null) {
            mother = balances().stream().map(StockBalance::dimension).filter(dimension -> code.equals(dimension.workOrderCode()))
                    .map(dimension -> mother(dimension.motherOrderCode())).filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (mother != null && visible(mother, operator, area)) return true;
        if (mother == null || !customerVisible(mother.customerId(), mother.customerName(), operator)) return false;
        return documents.listProcessStationHandoffs(code).stream().anyMatch(handoff -> operator.operator().equals(handoff.operator()))
                || documents.findQualityRequirementCheck(code).map(check -> operator.operator().equals(check.inspector())).orElse(false);
    }

    private boolean visible(MotherOrderRecord mother, OperatorContext operator, String area) {
        return mother != null && customerVisible(mother.customerId(), mother.customerName(), operator)
                && (areaVisible(area, operator) || List.of(text(mother.operator()), text(mother.receiverName()), text(mother.unpackSigner())).contains(operator.operator()));
    }

    private boolean visible(StockDimension dimension, OperatorContext operator) {
        if (dimension == null) return false;
        MotherOrderRecord mother = mother(dimension.motherOrderCode());
        return customerVisible(dimension.customerId(), mother == null ? "" : mother.customerName(), operator)
                && (areaVisible("inventory", operator) || visibleOrder(text(dimension.workOrderCode()).isEmpty() ? dimension.motherOrderCode() : dimension.workOrderCode(), operator, "inventory"));
    }

    private boolean areaVisible(String area, OperatorContext operator) {
        if (operator.dataScope() == DataScope.ALL || operator.dataScope() == DataScope.CUSTOMER_RANGE) return true;
        if (operator.dataScope() == DataScope.SELF) return false;
        // 尚无真实组织关系时，只按明确岗位职责开放队组/部门视图，其他岗位仅看本人关联记录。
        return switch (area) {
            case "inbound", "inventory" -> operator.role() == com.zhubao.erp.security.domain.RoleCode.RECEIVER
                    || operator.role() == com.zhubao.erp.security.domain.RoleCode.WAREHOUSE_KEEPER;
            case "production" -> operator.role() == com.zhubao.erp.security.domain.RoleCode.WORKSHOP_LEADER;
            case "quality" -> operator.role() == com.zhubao.erp.security.domain.RoleCode.QUALITY_INSPECTOR;
            case "loss" -> operator.role() == com.zhubao.erp.security.domain.RoleCode.WORKSHOP_LEADER
                    || operator.role() == com.zhubao.erp.security.domain.RoleCode.QUALITY_INSPECTOR
                    || operator.role() == com.zhubao.erp.security.domain.RoleCode.FINANCE_STATISTICIAN;
            default -> false;
        };
    }

    private static String defects(QualityRequirementCheck check) {
        return check == null ? "" : check.lines().stream().filter(line -> QualityVerdicts.needsReason(line.verdict()))
                .map(line -> line.clause() + "：" + line.reason()).reduce((left, right) -> left + "；" + right).orElse("");
    }

    private boolean customerVisible(Long customerId, String name, OperatorContext operator) {
        if (operator.dataScope() == DataScope.ALL) return true;
        if (operator.customerScope().isEmpty()) return operator.dataScope() != DataScope.CUSTOMER_RANGE;
        return operator.customerScope().contains(text(name)) || customerId != null && operator.customerScope().contains(customerId.toString());
    }

    private OperationContext contextForOrder(String code) {
        ChildWorkOrderRecord child = documents == null ? null : documents.findChildWorkOrderByCode(code).orElse(null);
        String motherCode = child == null ? balances().stream().map(StockBalance::dimension)
                .filter(dimension -> code.equals(dimension.workOrderCode())).map(StockDimension::motherOrderCode).findFirst().orElse(code) : child.motherOrderCode();
        return context(motherCode, code, Set.of(StockStatus.WIP, StockStatus.SEMI_FINISHED, StockStatus.FROZEN, StockStatus.REWORK));
    }

    private OperationContext context(String motherCode, String orderCode, Set<StockStatus> statuses) {
        List<StockBalance> matches = balances().stream().filter(FormalBusinessWorkspaceQuery::positive)
                .filter(balance -> Objects.equals(motherCode, balance.dimension().motherOrderCode()))
                .filter(balance -> matchesWorkOrder(motherCode, orderCode, balance.dimension()))
                .filter(balance -> statuses.contains(balance.dimension().stockStatus())).toList();
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

    private String ledgerOperator(StockLedgerEntry entry) {
        if (documents == null) return "";
        MotherOrderRecord mother = mother(entry.documentCode());
        if (mother != null && Objects.equals(mother.ledgerCode(), entry.code())) return text(mother.operator());
        return documents.listAllProcessStationHandoffs().stream().filter(handoff -> Objects.equals(handoff.ledgerCode(), entry.code()))
                .map(ProcessStationHandoff::operator).findFirst().orElse("");
    }

    private StockLedgerEntry latestLedger(StockDimension dimension) {
        if (stocks == null) return null;
        return stocks.ledgers().stream().filter(entry -> Objects.equals(dimension, entry.to()) || Objects.equals(dimension, entry.from()))
                .max(Comparator.comparing(StockLedgerEntry::bizTime).thenComparing(StockLedgerEntry::code)).orElse(null);
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
    private static String weight(BigDecimal value) { return number(value).stripTrailingZeros().toPlainString() + " g"; }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String customer(MotherOrderRecord mother) { return text(mother.customerName()).isEmpty() ? "客户#" + mother.customerId() : mother.customerName(); }
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
}
