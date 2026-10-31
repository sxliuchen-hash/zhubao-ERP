package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.BagWeightState;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.BusinessTargetReference;
import com.zhubao.erp.business.domain.NodePortionBalance;
import com.zhubao.erp.business.domain.NodePortionReader;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
import com.zhubao.erp.business.domain.PortionRelease;
import com.zhubao.erp.business.domain.PortionReleaseRepository;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.domain.RouteSnapshotRepository;
import com.zhubao.erp.business.domain.ShippableView;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 对明确份额放行或冻结，并给出厂内可发条件。
 * 合格但穿串未完、外协未回、未复称或冻结份额不能当成可发。重复放行不双计。
 */
@Service
public class PortionReleaseService {

    public enum Location {
        IN_HOUSE,
        OUTSOURCING
    }

    private final ActivityBagRepository bags;
    private final PortionInspectionRepository inspections;
    private final PortionReleaseRepository releases;
    private final RouteSnapshotRepository routes;
    private final NodePortionReader portions;
    private final WeighingService weighing;
    private final PermissionGuard permissions;
    private final BusinessWorkUnit workUnit;

    public PortionReleaseService(ActivityBagRepository bags, PortionInspectionRepository inspections,
                                 PortionReleaseRepository releases, RouteSnapshotRepository routes,
                                 NodePortionReader portions, WeighingService weighing,
                                 PermissionGuard permissions, BusinessWorkUnit workUnit) {
        this.bags = bags;
        this.inspections = inspections;
        this.releases = releases;
        this.routes = routes;
        this.portions = portions;
        this.weighing = weighing;
        this.permissions = permissions;
        this.workUnit = workUnit;
    }

    public PortionRelease release(String requestId, String bagId, int bagVersion, BigDecimal quantity, OperatorContext actor) {
        assertPermission(actor, PermissionCode.BUSINESS_QUALITY_RELEASE);
        return change(requestId, bagId, bagVersion, quantity, true, actor);
    }

    public PortionRelease freeze(String requestId, String bagId, int bagVersion, BigDecimal quantity, OperatorContext actor) {
        assertPermission(actor, PermissionCode.BUSINESS_QUALITY_FREEZE);
        return change(requestId, bagId, bagVersion, quantity, false, actor);
    }

    public ShippableView shippable(String bagId, Location location) {
        ActivityBag bag = bags.find(required(bagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        PortionRelease hold = releases.find(bag.id()).orElse(new PortionRelease(bag.id(), BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal free = hold.released().subtract(hold.frozen()).max(BigDecimal.ZERO);
        List<String> blocks = new ArrayList<>();
        if (location == Location.OUTSOURCING) {
            blocks.add("外协未回，不能按厂内现货发出");
        }
        if (hold.frozen().signum() > 0 && free.signum() == 0) {
            blocks.add("冻结份额不能发出");
        }
        if (weighing.current(bag.id()).state() != BagWeightState.KNOWN) {
            blocks.add("需要本份额的实际重量后才能转移或发货");
        }
        if (!stringingDone(bag)) {
            blocks.add("穿串未完，不能仅凭质检合格发出");
        }
        if (free.signum() == 0 && hold.released().signum() == 0) {
            blocks.add("质量尚未放行");
        }
        return new ShippableView(bag.id(), blocks.isEmpty() ? free : BigDecimal.ZERO, blocks);
    }

    private PortionRelease change(String requestId, String bagId, int bagVersion, BigDecimal quantity, boolean releasing, OperatorContext actor) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("放行或冻结数量必须为正数");
        }
        ActivityBag bag = bags.find(required(bagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        String action = releasing ? "PORTION_RELEASE" : "PORTION_FREEZE";
        String text = workUnit.execute(new CommandCall(
                requestId, actor.operator().trim(), action, "BAG", bag.id(), bag.id() + "|" + bagVersion + "|" + quantity.toPlainString()
        ), session -> apply(bag.id(), bagVersion, quantity, releasing, session));
        String[] parts = text.split("\\|", -1);
        return new PortionRelease(parts[0], new BigDecimal(parts[1]), new BigDecimal(parts[2]));
    }

    private String apply(String bagId, int bagVersion, BigDecimal quantity, boolean releasing, WorkSession session) {
        ActivityBag current = bags.lock(bagId, session).orElseThrow(() -> new BusinessException("袋不存在"));
        if (current.version() != bagVersion) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        PortionRelease hold = releases.find(bagId).orElse(new PortionRelease(bagId, BigDecimal.ZERO, BigDecimal.ZERO));
        BigDecimal passed = inspections.listByBag(bagId).stream().map(PortionInspection::pass).reduce(BigDecimal.ZERO, BigDecimal::add);
        PortionRelease next;
        if (releasing) {
            BigDecimal updated = hold.released().add(quantity);
            if (updated.compareTo(passed) > 0) {
                throw new BusinessException("放行数量不能超过已检合格数量");
            }
            next = new PortionRelease(bagId, updated, hold.frozen());
        } else {
            BigDecimal updated = hold.frozen().add(quantity);
            if (updated.compareTo(current.quantity()) > 0) {
                throw new BusinessException("冻结数量不能超过本袋份额");
            }
            next = new PortionRelease(bagId, hold.released(), updated);
        }
        releases.save(next, session);
        return next.bagId() + "|" + next.released().toPlainString() + "|" + next.frozen().toPlainString();
    }

    private boolean stringingDone(ActivityBag bag) {
        BusinessKind kind = BusinessTargetReference.parse(bag.targetCode()).kind();
        RouteSnapshot route = routes.list(kind, bag.targetCode()).stream().filter(RouteSnapshot::confirmed)
                .max(java.util.Comparator.comparingInt(RouteSnapshot::version)).orElse(null);
        if (route == null || !route.stepIds().contains("STRING")) {
            return true;
        }
        NodePortionBalance stringing = portions.find(bag.id(), "STRING").orElse(null);
        return stringing != null && stringing.arrived().signum() > 0
                && stringing.handled().compareTo(stringing.arrived()) == 0;
    }

    private void assertPermission(OperatorContext actor, PermissionCode permission) {
        if (actor == null || actor.role() == null
                || !permissions.effectivePermissions(actor.role(), actor.coverDuties()).contains(permission)) {
            throw new PermissionDeniedException("缺少质量放行或冻结权限");
        }
    }

    private static String required(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("袋不存在");
        }
        return bagId.trim();
    }
}
