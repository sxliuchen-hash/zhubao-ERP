package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.ProcessAvailableQuantity;
import com.zhubao.erp.business.domain.ProcessCatalog;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 按已到份额、已确认路线和有效权限给出当前可处理量。
 * 只读；不写库存、工资、交工或完成事实。上道没有交来时，不用原袋数量补齐。
 */
public class ProcessAvailabilityQuery {

    private final ActivityBagRepository bags;
    private final RouteSnapshotRepository routes;
    private final NodePortionReader portions;
    private final PermissionGuard permissions;

    public ProcessAvailabilityQuery(ActivityBagRepository bags, RouteSnapshotRepository routes,
                                    NodePortionReader portions, PermissionGuard permissions) {
        this.bags = bags;
        this.routes = routes;
        this.portions = portions;
        this.permissions = permissions;
    }

    public ProcessAvailableQuantity lookup(String bagId, OperatorContext actor) {
        ActivityBag bag = bags.find(required(bagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        List<String> blocks = new ArrayList<>();
        if (!canReport(actor)) {
            blocks.add("缺少生产报工权限");
        }
        if (!bag.activeLeaf()) {
            blocks.add("已关闭的父袋不计入可办理量");
            return answer(bag, "", "", BigDecimal.ZERO, "", "", blocks);
        }
        RouteSnapshot route = confirmedRoute(bag.targetCode());
        if (route == null || route.stepIds().isEmpty()) {
            blocks.add("路线尚未确认");
            return answer(bag, "", "", BigDecimal.ZERO, "", "", blocks);
        }
        List<StepView> steps = views(bag, route);
        List<StepView> open = steps.stream().filter(step -> step.remaining().signum() > 0).toList();
        if (open.size() > 1) {
            blocks.add("同一袋不能同时有多道待处理数量");
            return answer(bag, "", "", BigDecimal.ZERO, "", "", blocks);
        }
        if (open.size() == 1) {
            StepView current = open.get(0);
            StepView next = after(steps, current);
            return answer(bag, current.stepId(), current.stepName(), current.remaining(),
                    idOf(next), nameOf(next), blocks);
        }
        boolean anyArrived = steps.stream().anyMatch(step -> step.arrived().signum() > 0);
        StepView waiting = steps.stream()
                .filter(step -> step.index() > 0 && step.arrived().signum() == 0)
                .findFirst()
                .orElse(null);
        if (waiting != null && anyArrived) {
            blocks.add("上道未到，不能用原袋数量补齐");
            StepView next = after(steps, waiting);
            return answer(bag, waiting.stepId(), waiting.stepName(), BigDecimal.ZERO, idOf(next), nameOf(next), blocks);
        }
        StepView last = steps.get(steps.size() - 1);
        blocks.add(anyArrived ? "本路线已无待处理数量" : "本袋没有可处理数量");
        return answer(bag, last.stepId(), last.stepName(), BigDecimal.ZERO, "", "", blocks);
    }

    private boolean canReport(OperatorContext actor) {
        if (actor == null || actor.role() == null) {
            return false;
        }
        return permissions.effectivePermissions(actor.role(), actor.coverDuties())
                .contains(PermissionCode.BUSINESS_PRODUCTION_REPORT);
    }

    private RouteSnapshot confirmedRoute(String targetCode) {
        BusinessKind kind = BusinessTargetReference.parse(targetCode).kind();
        return routes.list(kind, targetCode).stream()
                .filter(RouteSnapshot::confirmed)
                .max(Comparator.comparingInt(RouteSnapshot::version))
                .orElse(null);
    }

    private List<StepView> views(ActivityBag bag, RouteSnapshot route) {
        List<StepView> steps = new ArrayList<>();
        List<String> ids = route.stepIds();
        for (int index = 0; index < ids.size(); index++) {
            String stepId = ids.get(index);
            Optional<NodePortionBalance> stored = portions.find(bag.id(), stepId);
            BigDecimal arrived;
            BigDecimal handled;
            if (stored.isPresent()) {
                arrived = stored.get().arrived();
                handled = stored.get().handled();
            } else if (index == 0) {
                arrived = bag.quantity();
                handled = BigDecimal.ZERO;
            } else {
                arrived = BigDecimal.ZERO;
                handled = BigDecimal.ZERO;
            }
            steps.add(new StepView(index, stepId, ProcessCatalog.require(stepId).name(), arrived, handled));
        }
        return List.copyOf(steps);
    }

    private static StepView after(List<StepView> steps, StepView current) {
        int next = current.index() + 1;
        return next < steps.size() ? steps.get(next) : null;
    }

    private static String idOf(StepView step) {
        return step == null ? "" : step.stepId();
    }

    private static String nameOf(StepView step) {
        return step == null ? "" : step.stepName();
    }

    private static ProcessAvailableQuantity answer(ActivityBag bag, String stepId, String stepName,
                                                   BigDecimal available, String nextStepId, String nextStepName,
                                                   List<String> blocks) {
        return new ProcessAvailableQuantity(
                bag.id(), bag.targetCode(), stepId, stepName, available, nextStepId, nextStepName, blocks);
    }

    private static String required(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("袋不存在");
        }
        return bagId.trim();
    }

    private record StepView(int index, String stepId, String stepName, BigDecimal arrived, BigDecimal handled) {
        private BigDecimal remaining() {
            return arrived.subtract(handled);
        }
    }
}
