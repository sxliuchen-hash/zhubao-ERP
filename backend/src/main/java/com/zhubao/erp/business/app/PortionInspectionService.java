package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.ActivityBagRepository;
import com.zhubao.erp.business.domain.PortionInspection;
import com.zhubao.erp.business.domain.PortionInspectionRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.domain.WorkSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 按袋记录质量检查。结论只作用于本次份额，不覆盖其他袋。
 */
@Service
public class PortionInspectionService {

    private final ActivityBagRepository bags;
    private final PortionInspectionRepository inspections;
    private final PermissionGuard permissions;
    private final BusinessWorkUnit workUnit;

    public PortionInspectionService(ActivityBagRepository bags, PortionInspectionRepository inspections,
                                    PermissionGuard permissions, BusinessWorkUnit workUnit) {
        this.bags = bags;
        this.inspections = inspections;
        this.permissions = permissions;
        this.workUnit = workUnit;
    }

    public PortionInspection record(String requestId, String bagId, int bagVersion, String requirementVersion,
                                    BigDecimal pass, BigDecimal rework, BigDecimal scrap, BigDecimal pending,
                                    List<String> failedClauses, OperatorContext actor) {
        if (actor == null || actor.role() == null || !canInspect(actor)) {
            throw new PermissionDeniedException("缺少质量检查权限");
        }
        BigDecimal passed = nonNegative(pass);
        BigDecimal reworked = nonNegative(rework);
        BigDecimal scrapped = nonNegative(scrap);
        BigDecimal held = nonNegative(pending);
        BigDecimal counted = passed.add(reworked).add(scrapped).add(held);
        if (counted.signum() == 0) {
            throw new BusinessException("请填写本次检查数量");
        }
        ActivityBag bag = bags.find(required(bagId)).orElseThrow(() -> new BusinessException("袋不存在"));
        if (requirementVersion == null || !requirementVersion.equals(bag.requirementVersion())) {
            throw new BusinessException("质量检查必须使用袋子上的要求版本");
        }
        String clauses = failedClauses == null ? "" : String.join(",", failedClauses);
        String payload = bag.id() + "|" + bagVersion + "|" + requirementVersion + "|"
                + passed.toPlainString() + "|" + reworked.toPlainString() + "|" + scrapped.toPlainString()
                + "|" + held.toPlainString() + "|" + clauses;
        String text = workUnit.execute(new CommandCall(
                requestId, actor.operator().trim(), "PORTION_INSPECTION", "BAG", bag.id(), payload
        ), session -> save(requestId, bag.id(), bagVersion, requirementVersion, passed, reworked, scrapped, held,
                failedClauses, counted, session));
        String[] parts = text.split("\\|", -1);
        return new PortionInspection(parts[0], requestId, bag.id(), requirementVersion, passed, reworked, scrapped, held,
                failedClauses);
    }

    private String save(String requestId, String bagId, int bagVersion, String requirementVersion,
                        BigDecimal pass, BigDecimal rework, BigDecimal scrap, BigDecimal pending,
                        List<String> failedClauses, BigDecimal counted, WorkSession session) {
        ActivityBag current = bags.find(bagId).orElseThrow(() -> new BusinessException("袋不存在"));
        if (current.version() != bagVersion) {
            throw new BusinessException("袋已被其他人修改，请刷新后重试");
        }
        if (!current.activeLeaf()) {
            throw new BusinessException("已关闭的袋不能再做质量检查");
        }
        BigDecimal already = inspections.listByBag(bagId).stream().map(PortionInspection::counted).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (already.add(counted).compareTo(current.quantity()) > 0) {
            throw new BusinessException("检查数量超过本袋份额");
        }
        String id = "QC" + UUID.randomUUID().toString().replace("-", "");
        inspections.save(new PortionInspection(id, requestId, bagId, requirementVersion, pass, rework, scrap, pending, failedClauses), session);
        return id + "|" + counted.toPlainString();
    }

    private boolean canInspect(OperatorContext actor) {
        var granted = permissions.effectivePermissions(actor.role(), actor.coverDuties());
        return granted.contains(PermissionCode.BUSINESS_QUALITY_RELEASE)
                || granted.contains(PermissionCode.BUSINESS_QUALITY_FREEZE);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal number = value == null ? BigDecimal.ZERO : value;
        if (number.signum() < 0) {
            throw new BusinessException("检查数量不能为负");
        }
        return number;
    }

    private static String required(String bagId) {
        if (bagId == null || bagId.isBlank()) {
            throw new BusinessException("袋不存在");
        }
        return bagId.trim();
    }
}
