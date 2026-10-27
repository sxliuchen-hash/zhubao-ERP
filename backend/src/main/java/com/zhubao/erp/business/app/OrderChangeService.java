package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.ExecutionRound;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OrderChange;
import com.zhubao.erp.business.domain.OrderChangeRepository;
import com.zhubao.erp.business.domain.OrderChangeStatus;
import com.zhubao.erp.business.domain.OrderRequirementRepository;
import com.zhubao.erp.business.domain.OrderRequirementVersion;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.WorkshopProcesses;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.OrderChangePostingService;
import com.zhubao.erp.label.domain.LabelTraceRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 改单只追加版本。生效前必须有财务确认，确认可以明确选择金额不变。
 * 生效后的要求、期限和路线写入当前版本，岗位下次查询读取该版本，不覆盖原单和已有交付。
 */
@Service
public class OrderChangeService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderChangeRepository changes;
    private final BusinessDocumentRepository documents;
    private final BusinessWorkUnit workUnit;
    private final OrderChangePostingService posting;
    private final BusinessDataAccess dataAccess;
    private final PermissionGuard permissionGuard;
    private final OrderRequirementRepository requirements;
    private LabelTraceRepository labelTraces;

    public OrderChangeService(OrderChangeRepository changes, BusinessDocumentRepository documents, BusinessWorkUnit workUnit) {
        this(changes, documents, workUnit, null);
    }

    public OrderChangeService(
            OrderChangeRepository changes,
            BusinessDocumentRepository documents,
            BusinessWorkUnit workUnit,
            OrderChangePostingService posting
    ) {
        this(changes, documents, workUnit, posting, null, null, null);
    }

    @Autowired
    public OrderChangeService(
            OrderChangeRepository changes,
            BusinessDocumentRepository documents,
            BusinessWorkUnit workUnit,
            OrderChangePostingService posting,
            BusinessDataAccess dataAccess,
            PermissionGuard permissionGuard,
            OrderRequirementRepository requirements
    ) {
        this.changes = changes;
        this.documents = documents;
        this.workUnit = workUnit;
        this.posting = posting;
        this.dataAccess = dataAccess;
        this.permissionGuard = permissionGuard;
        this.requirements = requirements;
    }

    @Autowired(required = false)
    public void setLabelTraces(LabelTraceRepository labelTraces) {
        this.labelTraces = labelTraces;
    }

    public OrderChange draft(
            String motherOrderCode,
            String workOrderCode,
            String newRequirement,
            String newDueDate,
            String requestId,
            AuthUserAccount account
    ) {
        return draft(motherOrderCode, workOrderCode, newRequirement, newDueDate, requestId, null, null, "", account);
    }

    public OrderChange draft(
            String motherOrderCode,
            String workOrderCode,
            String newRequirement,
            String newDueDate,
            String requestId,
            String newRoute,
            Integer expectedVersion,
            String reworkProcess,
            AuthUserAccount account
    ) {
        requireAccount(account);
        assertScope(motherOrderCode, workOrderCode, account);
        MotherOrderRecord mother = documents.findMotherOrderByCode(motherOrderCode)
                .orElseThrow(() -> new BusinessException("母单不存在：" + motherOrderCode));
        OrderChange existing = changes.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            assertScope(existing.motherOrderCode(), existing.workOrderCode(), account);
            assertSameDraft(existing, mother, workOrderCode, newRequirement, newDueDate, newRoute, reworkProcess, account);
            return visible(existing, account);
        }
        DraftFacts facts = facts(mother, workOrderCode, newRequirement, newDueDate, newRoute, reworkProcess, expectedVersion);
        if (newRoute != null && !newRoute.isBlank()) {
            for (String step : WorkshopProcesses.split(facts.route())) {
                assertProcess(step, account);
            }
        }
        assertProcess(facts.rework(), account);
        String now = TIME.format(LocalDateTime.now());
        int version = changes.listByMother(motherOrderCode).size() + 1;
        return visible(changes.save(new OrderChange(
                "CHG-" + UUID.randomUUID().toString().substring(0, 8),
                motherOrderCode,
                blank(workOrderCode),
                facts.oldRequirement(),
                facts.requirement(),
                facts.oldDue(),
                facts.due(),
                OrderChangeStatus.DRAFT,
                version,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                "",
                requestId,
                account.operator().trim(),
                now,
                now,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                "",
                "",
                facts.oldRoute(),
                facts.route(),
                facts.expected(),
                facts.rework()
        )), account);
    }

    public OrderChange submitFinance(String id, AuthUserAccount account) {
        OrderChange current = requireVisible(id, account);
        if (current.status() != OrderChangeStatus.DRAFT) {
            throw new BusinessException("只有草稿可以送审");
        }
        return visible(saveStatus(current, OrderChangeStatus.DEFERRED_TO_SETTLEMENT, false, BigDecimal.ZERO, BigDecimal.ZERO,
                "费用交货后确定", account.operator().trim()), account);
    }

    public OrderChange confirmFinance(String id, boolean amountUnchanged, BigDecimal customerUnitPrice, BigDecimal workerFee, String note, AuthUserAccount account) {
        throw new BusinessException("改单不再确认单价或工费，费用在交货后结单");
    }

    public OrderChange confirmFinance(
            String id,
            boolean amountUnchanged,
            BigDecimal customerUnitPrice,
            BigDecimal workerFee,
            BigDecimal customerDelta,
            BigDecimal workerDelta,
            String receivableCode,
            String processName,
            String workerName,
            String note,
            AuthUserAccount account
    ) {
        throw new BusinessException("改单不再确认单价或工费，费用在交货后结单");
    }

    public OrderChange rejectFinance(String id, String note, AuthUserAccount account) {
        requireFinance(account);
        OrderChange current = requireVisible(id, account);
        if (current.status() != OrderChangeStatus.PENDING_FINANCE) {
            throw new BusinessException("改单不在待财务确认状态");
        }
        return visible(saveStatus(current, OrderChangeStatus.REJECTED, false, BigDecimal.ZERO, BigDecimal.ZERO, note == null ? "" : note.trim(), account.operator().trim()), account);
    }

    public OrderChange apply(String id, String requestId, AuthUserAccount account) {
        return apply(id, requestId, null, account);
    }

    public OrderChange apply(String id, String requestId, Integer expectedVersion, AuthUserAccount account) {
        requireAccount(account);
        if (workUnit == null) {
            throw new BusinessException("改单回放尚未装配");
        }
        OrderChange current = requireVisible(id, account);
        if (!canApply(current.status())) {
            throw new BusinessException("业务审核后才能生效");
        }
        if (expectedVersion != null && expectedVersion != current.expectedVersion()) {
            throw new BusinessException("不能改写财务确认时固定的基准版本");
        }
        int expected = current.expectedVersion();
        String target = versionTarget(current.motherOrderCode(), current.workOrderCode());
        List<ProcessStationHandoff> before = documents.listProcessStationHandoffs(target);
        workUnit.execute(new CommandCall(
                requestId,
                account.operator().trim(),
                "ORDER_CHANGE_APPLY",
                "MOTHER_ORDER",
                current.motherOrderCode(),
                "change=" + current.id() + ";version=" + current.versionNo() + ";expected=" + expected
        ), session -> {
            documents.lockMotherOrder(current.motherOrderCode(), session);
            OrderChange latest = require(id);
            if (latest.status() == OrderChangeStatus.APPLIED) {
                return "applied";
            }
            if (latest.status() != OrderChangeStatus.DEFERRED_TO_SETTLEMENT
                    && latest.status() != OrderChangeStatus.FINANCE_CONFIRMED) {
                throw new BusinessException("业务审核后才能生效");
            }
            if (changes.latestAppliedVersion(target) != expected) {
                throw new BusinessException("改单版本已变化，请重新确认后再生效");
            }
            if (documents.supportsProcessBatches()) {
                // 母单锁与交工一致，避免在途批次的路线在生效过程中发生改变。
                for (var batch : documents.listAllProcessBatches()) {
                    boolean family = batch.orderCode().equals(latest.motherOrderCode())
                            || documents.findChildWorkOrderByCode(batch.orderCode()).map(child -> child.motherOrderCode().equals(latest.motherOrderCode())).orElse(false);
                    if (family && documents.findProcessStationHandoff(batch.orderCode(), batch.processName(), session).isEmpty())
                        throw new BusinessException("这张母单存在尚未交齐的批次，请完成在途交工后再生效改单");
                }
            }
            String now = TIME.format(LocalDateTime.now());
            String storedWork = storedWorkOrder(latest);
            if (requirements != null) {
                int existingVersion = requirements.current(latest.motherOrderCode(), storedWork)
                        .map(OrderRequirementVersion::versionNo).orElse(0);
                requirements.saveVersion(new OrderRequirementVersion(
                        "REQ-" + latest.id(),
                        latest.motherOrderCode(),
                        storedWork,
                        Math.max(latest.versionNo(), existingVersion + 1),
                        latest.newRequirement(),
                        latest.newDueDate(),
                        latest.newRoute(),
                        latest.id(),
                        now
                ), session);
                saveRoundIfNeeded(latest, storedWork, now, session);
            }
            changes.save(copy(latest, OrderChangeStatus.APPLIED, latest.amountUnchanged(), latest.customerUnitPrice(), latest.workerFee(), latest.financeNote(), latest.confirmedBy()), session);
            return "applied";
        });
        List<ProcessStationHandoff> after = documents.listProcessStationHandoffs(target);
        if (before.size() != after.size()) {
            throw new IllegalStateException("改单生效不得改写已有交付");
        }
        return visible(require(id), account);
    }

    public OrderChange present(String id, AuthUserAccount account) {
        return visible(requireVisible(id, account), account);
    }

    public List<OrderChange> list(String motherOrderCode, AuthUserAccount account) {
        assertScope(motherOrderCode, "", account);
        return changes.listByMother(motherOrderCode).stream().map(change -> visible(change, account)).toList();
    }

    public List<String> timeline(String motherOrderCode, AuthUserAccount account) {
        assertScope(motherOrderCode, "", account);
        boolean money = canSeeMoney(account);
        List<String> lines = new ArrayList<>();
        documents.findMotherOrderByCode(motherOrderCode).ifPresent(mother ->
                lines.add("收货 " + mother.code() + " " + mother.createdAt()));
        documents.listChildWorkOrders(motherOrderCode).forEach(child ->
                lines.add("子单 " + child.code() + " " + child.createdAt()));
        for (var child : documents.listChildWorkOrders(motherOrderCode)) {
            documents.listProcessStationHandoffs(child.code()).forEach(handoff ->
                    lines.add("交付 " + handoff.orderCode() + " " + handoff.processName() + " " + handoff.createdAt()));
        }
        documents.listProcessStationHandoffs(motherOrderCode).forEach(handoff ->
                lines.add("交付 " + handoff.orderCode() + " " + handoff.processName() + " " + handoff.createdAt()));
        if (requirements != null) {
            requirements.listByMother(motherOrderCode).forEach(round ->
                    lines.add("执行轮次 " + round.roundWorkOrderCode() + " 承接 " + round.supersedesOrderCode() + " " + round.processName()));
        }
        if (labelTraces != null) {
            labelTraces.listByMother(motherOrderCode).forEach(trace ->
                    lines.add("标签 " + trace.eventType() + " " + trace.businessCode() + " " + trace.reason()));
        }
        changes.listByMother(motherOrderCode).forEach(change -> lines.add(timelineLine(change, money)));
        return List.copyOf(lines);
    }

    private void saveRoundIfNeeded(OrderChange latest, String storedWork, String now, com.zhubao.erp.work.domain.WorkSession session) {
        if (latest.reworkProcess().isBlank()) {
            return;
        }
        String source = storedWork.isEmpty() ? latest.motherOrderCode() : storedWork;
        boolean handedOff = documents.listProcessStationHandoffs(source).stream()
                .anyMatch(handoff -> latest.reworkProcess().equals(handoff.processName()));
        if (!handedOff) {
            return;
        }
        requirements.saveRound(new ExecutionRound(
                "RND-" + latest.id(),
                latest.motherOrderCode(),
                source,
                "RW-" + latest.id(),
                latest.reworkProcess(),
                latest.id(),
                source,
                now
        ), session);
    }

    private String timelineLine(OrderChange change, boolean money) {
        String line = "改单 " + change.id() + " V" + change.versionNo() + " " + change.status()
                + " 原要求:" + change.oldRequirement() + " 新要求:" + change.newRequirement();
        if (money) {
            line += " 客户单价:" + change.customerUnitPrice() + " 工费:" + change.workerFee()
                    + " 差额:" + change.customerDelta() + " 应收:" + change.receivableCode();
        }
        return line;
    }

    private OrderChange saveStatus(OrderChange current, OrderChangeStatus status, boolean unchanged, BigDecimal price, BigDecimal fee, String note, String confirmedBy) {
        return changes.save(copy(current, status, unchanged, price, fee, note, confirmedBy));
    }

    private OrderChange copy(OrderChange current, OrderChangeStatus status, boolean unchanged, BigDecimal price, BigDecimal fee, String note, String confirmedBy) {
        return rebuild(current, status, unchanged, price, fee, note, confirmedBy, current.customerDelta(), current.workerDelta(), current.receivableCode(), current.processName(), current.workerName());
    }

    private OrderChange rebuild(
            OrderChange current,
            OrderChangeStatus status,
            boolean unchanged,
            BigDecimal price,
            BigDecimal fee,
            String note,
            String confirmedBy,
            BigDecimal customerDelta,
            BigDecimal workerDelta,
            String receivableCode,
            String processName,
            String workerName
    ) {
        return new OrderChange(
                current.id(), current.motherOrderCode(), current.workOrderCode(), current.oldRequirement(), current.newRequirement(),
                current.oldDueDate(), current.newDueDate(), status, current.versionNo(), unchanged, price, fee, note, confirmedBy,
                current.requestId(), current.createdBy(), current.createdAt(), TIME.format(LocalDateTime.now()),
                customerDelta, workerDelta, receivableCode, processName, workerName,
                current.oldRoute(), current.newRoute(), current.expectedVersion(), current.reworkProcess()
        );
    }

    private OrderChange requireVisible(String id, AuthUserAccount account) {
        OrderChange change = require(id);
        assertScope(change.motherOrderCode(), change.workOrderCode(), account);
        return change;
    }

    private OrderChange visible(OrderChange change, AuthUserAccount account) {
        return canSeeMoney(account) ? change : change.withoutMoney();
    }

    private OrderChange require(String id) {
        return changes.findById(id).orElseThrow(() -> new BusinessException("改单不存在：" + id));
    }

    /**
     * 响应丢失后的重试必须带回同一张草稿。内容或账号变了就拒绝，不能改成另一张申请。
     * 空白字段按保存时的继承结果比较，避免把“沿用当前版本”误判成另一份内容。
     */
    private void assertSameDraft(
            OrderChange existing,
            MotherOrderRecord mother,
            String workOrderCode,
            String newRequirement,
            String newDueDate,
            String newRoute,
            String reworkProcess,
            AuthUserAccount account
    ) {
        if (!account.operator().trim().equals(existing.createdBy())) {
            throw new PermissionDeniedException("该请求不能跨账号重放");
        }
        String incomingRequirement = newRequirement == null || newRequirement.isBlank()
                ? existing.newRequirement() : newRequirement.trim();
        String incomingDue = newDueDate == null || newDueDate.isBlank() ? existing.newDueDate() : newDueDate.trim();
        String incomingRework = normalizeOptionalProcess(reworkProcess);
        String incomingRoute = newRoute == null || newRoute.isBlank()
                ? existing.newRoute() : normalizeRoute(newRoute, existing.oldRoute());
        if (!existing.motherOrderCode().equals(mother.code())
                || !existing.workOrderCode().equals(blank(workOrderCode))
                || !existing.newRequirement().equals(incomingRequirement)
                || !existing.newDueDate().equals(incomingDue)
                || !existing.reworkProcess().equals(incomingRework)
                || !existing.newRoute().equals(incomingRoute)) {
            throw new BusinessException("同一请求的内容不一致");
        }
    }

    /**
     * 没写的要求、期限和路线继承当前有效版本，不退回原母单。基准版本只取已生效版本。
     */
    private DraftFacts facts(
            MotherOrderRecord mother,
            String workOrderCode,
            String newRequirement,
            String newDueDate,
            String newRoute,
            String reworkProcess,
            Integer expectedVersion
    ) {
        OrderRequirementVersion current = OrderRequirementLookup.current(requirements, mother.code(), blank(workOrderCode)).orElse(null);
        String oldRequirement = OrderRequirementLookup.requirement(current, blank(mother.processingRequirement()));
        String oldDue = OrderRequirementLookup.dueDate(current, blank(mother.dueDate()));
        String oldRoute = current != null && current.routeText() != null && !current.routeText().isBlank()
                ? current.routeText() : routeOf(mother, workOrderCode);
        String route = normalizeRoute(newRoute, oldRoute);
        String requirement = newRequirement == null || newRequirement.isBlank() ? oldRequirement : newRequirement.trim();
        String due = newDueDate == null || newDueDate.isBlank() ? oldDue : newDueDate.trim();
        int latest = changes.latestAppliedVersion(versionTarget(mother.code(), workOrderCode));
        int expected = expectedVersion == null ? latest : expectedVersion;
        if (expected != latest) {
            throw new BusinessException("改单基于的版本已过期");
        }
        return new DraftFacts(oldRequirement, requirement, oldDue, due, oldRoute, route, expected, normalizeOptionalProcess(reworkProcess));
    }

    private record DraftFacts(
            String oldRequirement,
            String requirement,
            String oldDue,
            String due,
            String oldRoute,
            String route,
            int expected,
            String rework
    ) {
    }

    private void assertScope(String motherOrderCode, String workOrderCode, AuthUserAccount account) {
        if (dataAccess != null) {
            dataAccess.assertDocumentCustomer(BusinessDataAccess.contextOf(account), motherOrderCode);
        }
        if (workOrderCode == null || workOrderCode.isBlank() || workOrderCode.equals(motherOrderCode)) {
            return;
        }
        ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(workOrderCode).orElse(null);
        if (child == null || !motherOrderCode.equals(child.motherOrderCode())) {
            throw new BusinessException("工单不属于这张母单");
        }
    }

    private void assertProcess(String process, AuthUserAccount account) {
        if (process == null || process.isBlank()) {
            return;
        }
        List<String> bound = WorkshopProcesses.bind(account.allowedProcesses());
        if (!bound.isEmpty() && !bound.contains(process)) {
            throw new PermissionDeniedException("当前账号不能操作工序：" + process);
        }
    }

    private String routeOf(MotherOrderRecord mother, String workOrderCode) {
        if (workOrderCode != null && !workOrderCode.isBlank() && !workOrderCode.equals(mother.code())) {
            ChildWorkOrderRecord child = documents.findChildWorkOrderByCode(workOrderCode).orElse(null);
            if (child != null && child.processes() != null && !child.processes().isBlank()) {
                return WorkshopProcesses.join(WorkshopProcesses.normalize(WorkshopProcesses.split(child.processes())));
            }
        }
        return WorkshopProcesses.join(WorkshopProcesses.DEFAULT_ORDER);
    }

    private String normalizeRoute(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        String normalized = requested.replace(',', '、').replace('，', '、');
        return WorkshopProcesses.join(WorkshopProcesses.normalize(WorkshopProcesses.split(normalized)));
    }

    private String normalizeOptionalProcess(String process) {
        if (process == null || process.isBlank()) {
            return "";
        }
        return WorkshopProcesses.normalize(List.of(process.trim())).get(0);
    }

    private String versionTarget(String motherOrderCode, String workOrderCode) {
        return workOrderCode == null || workOrderCode.isBlank() ? motherOrderCode : workOrderCode;
    }

    private String storedWorkOrder(OrderChange change) {
        if (change.workOrderCode().isBlank() || change.workOrderCode().equals(change.motherOrderCode())) {
            return "";
        }
        return change.workOrderCode();
    }

    private void requireAccount(AuthUserAccount account) {
        if (account == null || account.operator() == null || account.operator().isBlank()) {
            throw new BusinessException("请先登录后再改单");
        }
    }

    private boolean canApply(OrderChangeStatus status) {
        return status == OrderChangeStatus.DEFERRED_TO_SETTLEMENT
                || status == OrderChangeStatus.FINANCE_CONFIRMED
                || status == OrderChangeStatus.APPLIED;
    }

    private void requireFinance(AuthUserAccount account) {
        requireAccount(account);
        if (permissionGuard != null) {
            permissionGuard.assertAllowed(PermissionCode.FINANCE_BILLING_POST, BusinessDataAccess.contextOf(account));
            return;
        }
        if (account.role() != RoleCode.FINANCE_STATISTICIAN && account.role() != RoleCode.ADMIN && account.role() != RoleCode.GENERAL_MANAGER) {
            throw new PermissionDeniedException("只有财务或管理员可以确认改单金额");
        }
    }

    private boolean canSeeMoney(AuthUserAccount account) {
        if (account == null) {
            return false;
        }
        if (permissionGuard != null) {
            try {
                permissionGuard.assertAllowed(PermissionCode.FINANCE_BILLING_POST, BusinessDataAccess.contextOf(account));
                return true;
            } catch (PermissionDeniedException ex) {
                return false;
            }
        }
        return account.role() == RoleCode.FINANCE_STATISTICIAN
                || account.role() == RoleCode.ADMIN
                || account.role() == RoleCode.GENERAL_MANAGER;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
