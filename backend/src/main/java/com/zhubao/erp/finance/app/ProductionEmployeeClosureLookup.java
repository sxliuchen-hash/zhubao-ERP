package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 整单完工资格与计酬依据分开读取，按真实工序和账号核对作业，不从末道成品数推算工资。 */
@Service
public class ProductionEmployeeClosureLookup implements EmployeeTargetClosureLookup {
    private final ActivityBagRepository bags;
    private final RouteSnapshotRepository routes;
    private final NodePortionReader portions;
    private final ReworkTaskRepository rework;
    private final QuantityDispositionRepository dispositions;
    private final BusinessDocumentRepository documents;
    private final WholeBagReportRepository reports;
    private final OutsourcingCompletionRepository completions;
    private final OutsourcingSourceRepository outsourceTasks;
    private final OutsourcingDispatchRepository outsourceDispatches;
    private final OutsourcingReturnRepository outsourceReturns;

    public ProductionEmployeeClosureLookup(ActivityBagRepository bags, RouteSnapshotRepository routes, NodePortionReader portions,
            ReworkTaskRepository rework, OutsourcingSourceRepository tasks, OutsourcingDispatchRepository dispatches,
            QuantityDispositionRepository dispositions, BusinessDocumentRepository documents) {
        this(bags, routes, portions, rework, tasks, dispatches, null, dispositions, documents, null, null);
    }

    public ProductionEmployeeClosureLookup(ActivityBagRepository bags, RouteSnapshotRepository routes, NodePortionReader portions,
            ReworkTaskRepository rework, QuantityDispositionRepository dispositions, BusinessDocumentRepository documents,
            WholeBagReportRepository reports, OutsourcingCompletionRepository completions) {
        this(bags, routes, portions, rework, null, null, null, dispositions, documents, reports, completions);
    }

    @Autowired
    public ProductionEmployeeClosureLookup(ActivityBagRepository bags, RouteSnapshotRepository routes, NodePortionReader portions,
            ReworkTaskRepository rework, OutsourcingSourceRepository tasks, OutsourcingDispatchRepository dispatches,
            OutsourcingReturnRepository returns, QuantityDispositionRepository dispositions, BusinessDocumentRepository documents,
            WholeBagReportRepository reports, OutsourcingCompletionRepository completions) {
        this.bags = bags; this.routes = routes; this.portions = portions; this.rework = rework;
        this.dispositions = dispositions; this.documents = documents; this.reports = reports; this.completions = completions;
        this.outsourceTasks = tasks; this.outsourceDispatches = dispatches; this.outsourceReturns = returns;
    }

    @Override
    public Optional<EmployeeTargetClosure> find(String targetCode) {
        String target = targetCode == null ? "" : targetCode.trim();
        BusinessKind kind;
        try { kind = BusinessTargetReference.parse(target).kind(); }
        catch (BusinessException ex) { return Optional.empty(); }
        List<ActivityBag> all = bags.listByTarget(target);
        if (all.isEmpty() && documents.findMotherOrderByCode(target).isEmpty() && documents.findChildWorkOrderByCode(target).isEmpty()) return Optional.empty();
        List<ActivityBag> leaves = all.stream().filter(ActivityBag::activeLeaf).toList();
        List<QuantityDispositionLine> lines = all.stream().flatMap(bag -> dispositions.listByBag(bag.id()).stream()).toList();
        List<OutsourcingCompletion> external = completions == null ? List.of() : completions.listByTarget(target);
        BigDecimal outside = external.stream().map(OutsourcingCompletion::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loss = lines.isEmpty() || lines.stream().anyMatch(line -> line.loss() == null) ? null
                : lines.stream().map(QuantityDispositionLine::loss).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> outsourcedLoss = confirmedOutsourcingLoss(target);
        BigDecimal externalLoss = outsourcedLoss.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (externalLoss.signum() > 0) loss = loss == null ? externalLoss : loss.add(externalLoss);
        String unit = quantityUnit(target);
        RouteSnapshot route = routes.list(kind, target).stream().filter(RouteSnapshot::confirmed)
                .max(Comparator.comparingInt(RouteSnapshot::version)).orElse(null);
        if (route == null || route.stepIds().isEmpty()) return result(kind, EmployeeClosureKind.NOT_CLOSED, outside, loss, unit, new Basis(List.of(), false));
        Basis basis = work(target, all, leaves, route, lines, external, outsourcedLoss);
        BigDecimal assigned = all.stream().filter(bag -> bag.parentId() == null || bag.parentId().isBlank())
                .map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = lines.stream().map(QuantityDispositionLine::returned).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (leaves.isEmpty()) {
            EmployeeClosureKind state = loss != null && loss.signum() > 0 && loss.compareTo(assigned.subtract(returned)) == 0
                    ? EmployeeClosureKind.TOTAL_LOSS : EmployeeClosureKind.NOT_CLOSED;
            return result(kind, state, outside, loss, unit, basis);
        }
        List<ReworkTask> tasks = all.stream().flatMap(bag -> rework.listBySource(bag.id()).stream()).toList();
        boolean reworkOpen = tasks.stream().map(task -> bags.find(task.reworkBagId()).orElse(null))
                .anyMatch(bag -> bag != null && bag.activeLeaf() && remaining(bag, route));
        if (reworkOpen) return result(kind, EmployeeClosureKind.REWORK_OPEN, outside, loss, unit, basis);
        if (leaves.stream().anyMatch(bag -> remaining(bag, route))) return result(kind, EmployeeClosureKind.NOT_CLOSED, outside, loss, unit, basis);
        // 完成返工不等于计酬规则已确认，保留普通实作供核对，整单工资仍受返工门槛约束。
        return result(kind, tasks.isEmpty() ? EmployeeClosureKind.CLOSED_NORMAL : EmployeeClosureKind.REWORK_PAY_PENDING, outside, loss, unit, basis);
    }

    private Optional<EmployeeTargetClosure> result(BusinessKind kind, EmployeeClosureKind state, BigDecimal outside,
            BigDecimal loss, String unit, Basis basis) {
        BigDecimal employee = basis.complete ? basis.work.stream().map(EmployeeProcessWork::quantity).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        return Optional.of(new EmployeeTargetClosure(kind, state, employee, outside, loss, unit, basis.work, basis.complete));
    }

    private boolean remaining(ActivityBag bag, RouteSnapshot route) {
        for (String step : route.stepIds()) {
            var balance = portions.find(bag.id(), step);
            if (balance.isEmpty() || balance.get().remaining().signum() > 0 || balance.get().arrived().compareTo(bag.quantity()) < 0) return true;
        }
        return false;
    }

    private Basis work(String target, List<ActivityBag> all, List<ActivityBag> leaves, RouteSnapshot route,
            List<QuantityDispositionLine> lines, List<OutsourcingCompletion> external, Map<String, BigDecimal> outsourcedLoss) {
        Set<String> reworkBags = new HashSet<>();
        all.forEach(bag -> rework.listBySource(bag.id()).forEach(task -> reworkBags.add(task.reworkBagId())));
        boolean changed;
        do {
            changed = false;
            for (ActivityBag bag : all) if (reworkBags.contains(bag.parentId())) changed |= reworkBags.add(bag.id());
        } while (changed);
        Map<String, EmployeeProcessWork> workers = new LinkedHashMap<>();
        Map<String, BigDecimal> handled = new HashMap<>();
        boolean complete = reports != null && completions != null && reworkBags.isEmpty();
        if (reports != null) for (WholeBagReport report : reports.listByTarget(target)) {
            if (reworkBags.contains(report.bagId())) continue;
            handled.merge(report.stepId(), report.quantity(), BigDecimal::add);
            if (report.workerAccount() == null || report.workerAccount().isBlank() || !route.stepIds().contains(report.stepId())) { complete = false; continue; }
            add(workers, report.workerAccount(), report.operatorName(), report.stepId(), report.quantity());
        }
        for (QuantityDispositionLine line : lines) {
            if (reworkBags.contains(line.bagId())) continue;
            BigDecimal processed = line.handoff().add(line.loss()).add(line.scrap()).add(line.returned());
            if (processed.signum() == 0) continue;
            handled.merge(line.stepId(), processed, BigDecimal::add);
            if (line.workQuantity() == null || line.workerAccount() == null || line.workerAccount().isBlank()
                    || line.attachmentCode() == null || line.attachmentCode().isBlank() || !route.stepIds().contains(line.stepId())) { complete = false; continue; }
            if (line.workQuantity().signum() > 0) add(workers, line.workerAccount(), line.workerName(), line.stepId(), line.workQuantity());
        }
        external.stream().filter(item -> !reworkBags.contains(item.bagId()))
                .forEach(item -> handled.merge(item.nodeId(), item.quantity(), BigDecimal::add));
        // 分袋会继承节点进度，历史父袋不能重复计数；后续工序退出的份额已在此前工序处理过。
        for (int index = 0; index < route.stepIds().size(); index++) {
            String step = route.stepIds().get(index);
            BigDecimal expected = leaves.stream().map(bag -> portions.find(bag.id(), step).map(NodePortionBalance::handled).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (QuantityDispositionLine line : lines) {
                int exitedAt = route.stepIds().indexOf(line.stepId());
                if (exitedAt >= index) expected = expected.add(line.loss()).add(line.scrap()).add(line.returned());
            }
            // 外协节点确认的损耗不是该节点完工，但此前厂内工序已经处理过这些数量。
            for (Map.Entry<String, BigDecimal> lost : outsourcedLoss.entrySet()) {
                int lostAt = route.stepIds().indexOf(lost.getKey());
                if (lostAt > index) expected = expected.add(lost.getValue());
            }
            if (handled.getOrDefault(step, BigDecimal.ZERO).compareTo(expected) != 0) complete = false;
        }
        return new Basis(List.copyOf(workers.values()), complete);
    }

    private static void add(Map<String, EmployeeProcessWork> workers, String account, String name, String step, BigDecimal quantity) {
        String key = account + "\n" + step;
        EmployeeProcessWork current = workers.get(key);
        workers.put(key, new EmployeeProcessWork(account, name, step, ProcessCatalog.require(step).name(),
                current == null ? quantity : current.quantity().add(quantity)));
    }

    private Map<String, BigDecimal> confirmedOutsourcingLoss(String target) {
        if (outsourceTasks == null || outsourceDispatches == null || outsourceReturns == null) return Map.of();
        Map<String, BigDecimal> loss = new LinkedHashMap<>();
        for (OutsourcingTask task : outsourceTasks.listByTarget(target)) {
            for (OutsourcingDispatch dispatch : outsourceDispatches.listByTask(task.taskId())) {
                for (OutsourcingReturn row : outsourceReturns.listByDispatch(dispatch.dispatchId())) {
                    if (row.lossQty() != null && row.lossQty().signum() > 0) loss.merge(task.nodeId(), row.lossQty(), BigDecimal::add);
                }
            }
        }
        return loss;
    }

    private String quantityUnit(String target) {
        return documents.findChildWorkOrderByCode(target).map(order -> order.quantityUnit() == null ? "" : order.quantityUnit())
                .or(() -> documents.findMotherOrderByCode(target).map(order -> order.quantityUnit() == null ? "" : order.quantityUnit())).orElse("");
    }
    private record Basis(List<EmployeeProcessWork> work, boolean complete) {}
}
