package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.OutsourcingDispatchRepository;
import com.zhubao.erp.business.domain.OutsourcingSourceRepository;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.domain.QuantityDispositionLine;
import com.zhubao.erp.business.domain.QuantityDispositionRepository;
import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ReworkTaskRepository;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.EmployeeClosureKind;
import com.zhubao.erp.finance.domain.EmployeeTargetClosure;
import com.zhubao.erp.finance.domain.EmployeeTargetClosureLookup;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 从袋、已确认路线、返工和外协发外读取整单是否可以核算工资。
 * 外协数量不并进员工实作。没有处置记录时损耗保持空，不写成 0。
 */
@Service
public class ProductionEmployeeClosureLookup implements EmployeeTargetClosureLookup {

    private final ActivityBagRepository bags;
    private final RouteSnapshotRepository routes;
    private final NodePortionReader portions;
    private final ReworkTaskRepository rework;
    private final OutsourcingSourceRepository tasks;
    private final OutsourcingDispatchRepository dispatches;
    private final QuantityDispositionRepository dispositions;
    private final BusinessDocumentRepository documents;

    public ProductionEmployeeClosureLookup(ActivityBagRepository bags, RouteSnapshotRepository routes,
                                           NodePortionReader portions, ReworkTaskRepository rework,
                                           OutsourcingSourceRepository tasks, OutsourcingDispatchRepository dispatches,
                                           QuantityDispositionRepository dispositions, BusinessDocumentRepository documents) {
        this.bags = bags;
        this.routes = routes;
        this.portions = portions;
        this.rework = rework;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.dispositions = dispositions;
        this.documents = documents;
    }

    @Override
    public Optional<EmployeeTargetClosure> find(String targetCode) {
        String target = targetCode == null ? "" : targetCode.trim();
        BusinessKind kind;
        try {
            kind = BusinessTargetReference.parse(target).kind();
        } catch (BusinessException ex) {
            return Optional.empty();
        }
        List<ActivityBag> all = bags.listByTarget(target);
        List<ActivityBag> leaves = all.stream().filter(ActivityBag::activeLeaf).toList();
        if (leaves.isEmpty()) {
            return Optional.empty();
        }
        String unit = quantityUnit(target);
        BigDecimal outsourcing = outsourcingQuantity(target);
        BigDecimal loss = lossQuantity(leaves);
        RouteSnapshot route = confirmed(kind, target);
        if (route == null || route.stepIds().isEmpty()) {
            return Optional.of(new EmployeeTargetClosure(
                    kind, EmployeeClosureKind.NOT_CLOSED, BigDecimal.ZERO, outsourcing, loss, unit));
        }
        if (reworkStillOpen(all, route)) {
            return Optional.of(new EmployeeTargetClosure(
                    kind, EmployeeClosureKind.REWORK_OPEN, BigDecimal.ZERO, outsourcing, loss, unit));
        }
        boolean open = false;
        BigDecimal finished = BigDecimal.ZERO;
        String lastStep = route.stepIds().get(route.stepIds().size() - 1);
        for (ActivityBag leaf : leaves) {
            if (remaining(leaf, route)) {
                open = true;
            }
            finished = finished.add(handled(leaf.id(), lastStep));
        }
        BigDecimal employee = finished.subtract(outsourcing);
        if (employee.signum() < 0) {
            employee = BigDecimal.ZERO;
        }
        if (open) {
            return Optional.of(new EmployeeTargetClosure(
                    kind, EmployeeClosureKind.NOT_CLOSED, employee, outsourcing, loss, unit));
        }
        BigDecimal leafQuantity = leaves.stream().map(ActivityBag::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (employee.signum() == 0 && loss != null && loss.compareTo(leafQuantity) == 0 && loss.signum() > 0) {
            return Optional.of(new EmployeeTargetClosure(
                    kind, EmployeeClosureKind.TOTAL_LOSS, employee, outsourcing, loss, unit));
        }
        return Optional.of(new EmployeeTargetClosure(
                kind, EmployeeClosureKind.CLOSED_NORMAL, employee, outsourcing, loss, unit));
    }

    private boolean reworkStillOpen(List<ActivityBag> bagsOnTarget, RouteSnapshot route) {
        for (ActivityBag bag : bagsOnTarget) {
            for (ReworkTask task : rework.listBySource(bag.id())) {
                ActivityBag reworkBag = this.bags.find(task.reworkBagId()).orElse(null);
                if (reworkBag != null && reworkBag.activeLeaf() && remaining(reworkBag, route)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean remaining(ActivityBag bag, RouteSnapshot route) {
        List<String> steps = route.stepIds();
        for (int index = 0; index < steps.size(); index++) {
            Optional<NodePortionBalance> stored = portions.find(bag.id(), steps.get(index));
            BigDecimal arrived;
            BigDecimal done;
            if (stored.isPresent()) {
                arrived = stored.get().arrived();
                done = stored.get().handled();
            } else if (index == 0) {
                arrived = bag.quantity();
                done = BigDecimal.ZERO;
            } else {
                continue;
            }
            if (arrived.subtract(done).signum() > 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal handled(String bagId, String stepId) {
        return portions.find(bagId, stepId).map(NodePortionBalance::handled).orElse(BigDecimal.ZERO);
    }

    private BigDecimal outsourcingQuantity(String target) {
        BigDecimal total = BigDecimal.ZERO;
        for (OutsourcingTask task : tasks.listByTarget(target)) {
            total = total.add(dispatches.listByTask(task.taskId()).stream()
                    .map(dispatch -> dispatch.quantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return total;
    }

    private BigDecimal lossQuantity(List<ActivityBag> leaves) {
        boolean seen = false;
        BigDecimal total = BigDecimal.ZERO;
        for (ActivityBag leaf : leaves) {
            for (QuantityDispositionLine line : dispositions.listByBag(leaf.id())) {
                if (line.loss() == null) {
                    continue;
                }
                seen = true;
                total = total.add(line.loss());
            }
        }
        return seen ? total : null;
    }

    private RouteSnapshot confirmed(BusinessKind kind, String target) {
        return routes.list(kind, target).stream()
                .filter(RouteSnapshot::confirmed)
                .max(Comparator.comparingInt(RouteSnapshot::version))
                .orElse(null);
    }

    private String quantityUnit(String target) {
        return documents.findChildWorkOrderByCode(target)
                .map(order -> order.quantityUnit() == null ? "" : order.quantityUnit())
                .or(() -> documents.findMotherOrderByCode(target).map(order -> order.quantityUnit() == null ? "" : order.quantityUnit()))
                .orElse("");
    }
}
