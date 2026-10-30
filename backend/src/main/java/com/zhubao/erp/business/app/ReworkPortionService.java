package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
import com.zhubao.erp.business.domain.ReworkTask;
import com.zhubao.erp.business.domain.ReworkTaskRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 按原不合格份额开返工。复检只记在返工袋上。不新建母单，不自动决定返工计酬。
 */
@Service
public class ReworkPortionService {

    private final ActivityBagRepository bags;
    private final ActivityBagService bagService;
    private final PortionInspectionRepository inspections;
    private final PortionInspectionService inspectionService;
    private final ReworkTaskRepository tasks;
    private final PermissionGuard permissions;
    private final BusinessWorkUnit workUnit;

    public ReworkPortionService(ActivityBagRepository bags, ActivityBagService bagService,
                                PortionInspectionRepository inspections, PortionInspectionService inspectionService,
                                ReworkTaskRepository tasks, PermissionGuard permissions, BusinessWorkUnit workUnit) {
        this.bags = bags;
        this.bagService = bagService;
        this.inspections = inspections;
        this.inspectionService = inspectionService;
        this.tasks = tasks;
        this.permissions = permissions;
        this.workUnit = workUnit;
    }

    public ReworkTask open(String requestId, String sourceBagId, int bagVersion, int round, OperatorContext actor) {
        if (actor == null || actor.role() == null
                || !permissions.effectivePermissions(actor.role(), actor.coverDuties()).contains(PermissionCode.BUSINESS_REWORK_DISPATCH)) {
            throw new PermissionDeniedException("缺少返工派工权限");
        }
        if (round < 1) {
            throw new BusinessException("返工轮次必须从 1 开始");
        }
        ActivityBag source = bags.find(required(sourceBagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        BigDecimal rework = inspections.listByBag(source.id()).stream()
                .map(PortionInspection::rework).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rework.signum() <= 0) {
            throw new BusinessException("没有可返工的不合格份额");
        }
        String payload = source.id() + "|" + bagVersion + "|" + round + "|" + rework.toPlainString();
        String text = workUnit.execute(new CommandCall(
                requestId, actor.operator().trim(), "REWORK_PORTION", "BAG", source.id(), payload
        ), session -> create(requestId, source.id(), bagVersion, round, rework, session));
        String[] parts = text.split("\\|", -1);
        return new ReworkTask(parts[0], requestId, parts[1], parts[2], parts[3], parts[4], Integer.parseInt(parts[5]), new BigDecimal(parts[6]));
    }

    public PortionInspection reinspect(String requestId, String reworkBagId, int bagVersion,
                                       BigDecimal pass, BigDecimal rework, BigDecimal scrap, BigDecimal pending,
                                       OperatorContext actor) {
        ActivityBag bag = bags.find(required(reworkBagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        ReworkTask task = tasks.listBySource(bag.parentId()).stream()
                .filter(item -> item.reworkBagId().equals(bag.id())).findFirst().orElse(null);
        if (task == null) {
            throw new BusinessException("复检只能作用于返工份额");
        }
        return inspectionService.record(requestId, bag.id(), bagVersion, task.requirementVersion(),
                pass, rework, scrap, pending, java.util.List.of(), actor);
    }

    private String create(String requestId, String sourceBagId, int bagVersion, int round, BigDecimal rework, WorkSession session) {
        ActivityBag source = bags.find(sourceBagId).orElseThrow(() -> new BusinessException("袋不存在"));
        if (source.version() != bagVersion) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        if (!source.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能再开返工");
        }
        ActivityBag reworkBag = child(source, rework, session);
        BigDecimal remainder = source.quantity().subtract(rework);
        if (remainder.signum() > 0) {
            child(source, remainder, session);
        }
        bagService.close(source.id(), session);
        String id = "RW" + UUID.randomUUID().toString().replace("-", "");
        ReworkTask task = new ReworkTask(id, requestId, source.id(), reworkBag.id(), source.targetCode(),
                source.requirementVersion(), round, rework);
        tasks.save(task, session);
        return id + "|" + source.id() + "|" + reworkBag.id() + "|" + source.targetCode() + "|"
                + source.requirementVersion() + "|" + round + "|" + rework.toPlainString();
    }

    private ActivityBag child(ActivityBag source, BigDecimal quantity, WorkSession session) {
        ActivityBag child = new ActivityBag(
                "BG" + UUID.randomUUID().toString().replace("-", ""), source.targetCode(), source.materialCode(),
                source.requirementVersion(), source.sourceKey(), source.id(), false, quantity,
                StockMeasurement.unknown(), 1);
        bags.save(child, 0, session);
        return child;
    }

    private static String required(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("袋不存在");
        }
        return bagId.trim();
    }
}
