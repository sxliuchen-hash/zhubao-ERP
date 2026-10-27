package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.AccountPreApprovalTaskCommand;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApprovalService {

    private static final DateTimeFormatter ACCOUNT_APPROVAL_CODE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ApprovalTaskRepository repository;
    private final BusinessDataAccess businessDataAccess;
    private final ApprovalAccess access = new ApprovalAccess();
    private final AtomicLong accountApprovalSequence = new AtomicLong();
    private final AtomicLong accountPreApprovalSequence = new AtomicLong();

    public ApprovalService(ApprovalTaskRepository repository) {
        this(repository, null);
    }

    @Autowired
    public ApprovalService(ApprovalTaskRepository repository, BusinessDataAccess businessDataAccess) {
        this.repository = repository;
        this.businessDataAccess = businessDataAccess;
    }

    public ApprovalTask createLossApprovalTask(ApproveLossCommand command, LossApprovalResult result) {
        String createdAt = now();
        ApprovalStatus status = result.overLimit() ? ApprovalStatus.WAITING_APPROVAL : ApprovalStatus.AUTO_APPROVED;
        ApprovalTask task = new ApprovalTask(
                result.approvalCode(),
                "LOSS_APPROVAL",
                result.workOrderCode(),
                "Loss approval for " + result.workOrderCode() + " / " + result.process(),
                status,
                result.operator(),
                result.approver(),
                "WORKSHOP_LEADER",
                result.overLimit() ? "red" : "green",
                result.approvalStatus(),
                buildLossSummary(result),
                result.evidenceCode(),
                result.formalPayroll(),
                createdAt,
                status == ApprovalStatus.AUTO_APPROVED ? createdAt : null,
                status == ApprovalStatus.AUTO_APPROVED ? "SYSTEM" : null,
                status == ApprovalStatus.AUTO_APPROVED ? "Within configured loss threshold." : null,
                status == ApprovalStatus.AUTO_APPROVED
        );
        return repository.save(task);
    }

    public ApprovalTask createAccountManagementApprovalTask(
            String targetUsername,
            String action,
            String applicant,
            String applicantRole,
            String targetOperator,
            String targetRole,
            String targetDataScope,
            String targetCustomerScope,
            boolean targetEnabled,
            boolean demoAccount
    ) {
        String createdAt = now();
        ApprovalTask task = new ApprovalTask(
                nextAccountApprovalCode(),
                "ACCOUNT_MANAGEMENT",
                targetUsername,
                "Account management review / " + targetUsername,
                ApprovalStatus.WAITING_APPROVAL,
                blankToDefault(applicant, "SYSTEM"),
                "总经理 / IT 管理员",
                "GENERAL_MANAGER",
                targetEnabled ? "amber" : "red",
                "WAITING_ACCOUNT_REVIEW",
                buildAccountManagementSummary(
                        action,
                        applicantRole,
                        targetOperator,
                        targetRole,
                        targetDataScope,
                        targetCustomerScope,
                        targetEnabled,
                        demoAccount
                ),
                "",
                false,
                createdAt,
                null,
                null,
                null,
                false
        );
        return repository.save(task);
    }

    public ApprovalTask createAccountPreApprovalTask(AccountPreApprovalTaskCommand command) {
        String createdAt = now();
        String ownerRole = firstOrDefault(command.approverRoles(), "GENERAL_MANAGER");
        ApprovalTask task = new ApprovalTask(
                nextAccountPreApprovalCode(),
                "ACCOUNT_PRE_APPROVAL",
                command.applicationCode(),
                "Account pre-approval review / " + command.targetUsername(),
                ApprovalStatus.WAITING_APPROVAL,
                blankToDefault(command.applicant(), "SYSTEM"),
                joinList(command.approverRoles()).isBlank() ? ownerRole : joinList(command.approverRoles()),
                ownerRole,
                preApprovalSeverity(command),
                command.applicationStatus(),
                buildAccountPreApprovalSummary(command),
                command.applicationCode(),
                false,
                createdAt,
                null,
                null,
                null,
                false
        );
        return repository.save(task);
    }

    /**
     * 未按身份过滤。工资扣减等内部计算使用；对外列表必须走 {@link #visibleTasks}。
     */
    public List<ApprovalTask> findAll() {
        return repository.findAll()
                .stream()
                .sorted(Comparator.comparing(ApprovalTask::createdAt).reversed())
                .toList();
    }

    /**
     * 未按身份过滤的待审批任务，仅供内部计算。对外待办使用 {@link #pendingTasks}。
     */
    public List<ApprovalTask> findPending() {
        return findAll()
                .stream()
                .filter(task -> task.status() == ApprovalStatus.WAITING_APPROVAL)
                .toList();
    }

    public List<ApprovalTask> visibleTasks(ApprovalActor actor) {
        requireActor(actor);
        return findAll().stream().filter(task -> canRead(actor, task)).toList();
    }

    public List<ApprovalTask> pendingTasks(ApprovalActor actor) {
        requireActor(actor);
        return findAll().stream().filter(task -> access.canDecide(actor, task) && withinBusinessScope(actor, task)).toList();
    }

    public ApprovalTask findByCode(String approvalCode) {
        return repository.findByCode(approvalCode)
                .orElseThrow(() -> new BusinessException(404, "Approval task not found: " + approvalCode));
    }

    public ApprovalTask visibleTask(String approvalCode, ApprovalActor actor) {
        requireActor(actor);
        ApprovalTask task = findByCode(approvalCode);
        if (!canRead(actor, task)) {
            throw new PermissionDeniedException("无权查看该审批任务");
        }
        return task;
    }

    public ApprovalTask decide(String approvalCode, ApprovalDecisionCommand command, ApprovalActor actor) {
        requireActor(actor);
        ApprovalTask current = findByCode(approvalCode);
        access.assertCanDecide(actor, current);
        if (!withinBusinessScope(actor, current)) {
            throw new PermissionDeniedException("单据不在当前账号的审批数据范围内");
        }
        ApprovalStatus targetStatus = parseDecision(command.decision());
        ApprovalTask decided = new ApprovalTask(
                current.approvalCode(),
                current.scene(),
                current.businessCode(),
                current.title(),
                targetStatus,
                current.applicant(),
                current.approver(),
                current.ownerRole(),
                targetStatus == ApprovalStatus.APPROVED ? "green" : "red",
                current.sourceStatus(),
                current.sourceSummary(),
                command.evidenceCode() == null || command.evidenceCode().isBlank()
                        ? current.evidenceCode()
                        : command.evidenceCode(),
                current.formalPayroll(),
                current.createdAt(),
                now(),
                actor.operator(),
                command.comment(),
                true
        );
        return repository.decideIfWaiting(decided)
                .orElseThrow(() -> new BusinessException("该审批已处理，不能重复决策"));
    }

    private boolean canRead(ApprovalActor actor, ApprovalTask task) {
        return access.canRead(actor, task) && withinBusinessScope(actor, task);
    }

    private boolean withinBusinessScope(ApprovalActor actor, ApprovalTask task) {
        if (!ApprovalAccess.LOSS.equals(task.scene()) || actor.role() == RoleCode.ADMIN
                || (actor.operator().equals(task.applicant()) || actor.username().equalsIgnoreCase(task.applicant()))) {
            return true;
        }
        return businessDataAccess == null || (actor.context() != null
                && businessDataAccess.canRead(actor.context(), task.businessCode()));
    }

    private void requireActor(ApprovalActor actor) {
        if (actor == null || actor.role() == null || actor.operator().isBlank()) {
            throw new PermissionDeniedException("请先登录后再查看审批");
        }
    }

    private ApprovalStatus parseDecision(String decision) {
        String normalized = decision.trim().toUpperCase();
        if ("APPROVE".equals(normalized) || "APPROVED".equals(normalized)) {
            return ApprovalStatus.APPROVED;
        }
        if ("REJECT".equals(normalized) || "REJECTED".equals(normalized)) {
            return ApprovalStatus.REJECTED;
        }
        throw new BusinessException("Unsupported approval decision: " + decision);
    }

    private String buildLossSummary(LossApprovalResult result) {
        return "inputWeight=" + result.inputWeight()
                + "; outputWeight=" + result.outputWeight()
                + "; lossWeight=" + result.lossWeight()
                + "; allowedLossWeight=" + result.allowedLossWeight()
                + "; excessLossWeight=" + result.excessLossWeight()
                + "; wageTrial=deferred"
                + "; formalPayroll=" + result.formalPayroll();
    }

    private String buildAccountManagementSummary(
            String action,
            String applicantRole,
            String targetOperator,
            String targetRole,
            String targetDataScope,
            String targetCustomerScope,
            boolean targetEnabled,
            boolean demoAccount
    ) {
        return "action=" + blankToDefault(action, "账号管理变更")
                + "; applicantRole=" + blankToDefault(applicantRole, "UNKNOWN")
                + "; targetOperator=" + blankToDefault(targetOperator, "")
                + "; targetRole=" + blankToDefault(targetRole, "UNKNOWN")
                + "; targetDataScope=" + blankToDefault(targetDataScope, "UNKNOWN")
                + "; targetCustomerScope=" + blankToDefault(targetCustomerScope, "")
                + "; enabled=" + targetEnabled
                + "; demoAccount=" + demoAccount
                + "; boundary=post-action approval summary, not a blocking IAM workflow";
    }

    private String buildAccountPreApprovalSummary(AccountPreApprovalTaskCommand command) {
        return "applicationCode=" + blankToDefault(command.applicationCode(), "")
                + "; readinessCode=" + blankToDefault(command.readinessCode(), "")
                + "; actionType=" + blankToDefault(command.actionType(), "")
                + "; targetUsername=" + blankToDefault(command.targetUsername(), "")
                + "; applicantRole=" + blankToDefault(command.applicantRole(), "UNKNOWN")
                + "; targetOperator=" + blankToDefault(command.targetOperator(), "")
                + "; targetRole=" + blankToDefault(command.targetRole(), "UNKNOWN")
                + "; targetDataScope=" + blankToDefault(command.targetDataScope(), "UNKNOWN")
                + "; targetCustomerScope=" + joinList(command.targetCustomerScope())
                + "; applicationStatus=" + blankToDefault(command.applicationStatus(), "UNKNOWN")
                + "; blockingStatus=" + blankToDefault(command.blockingStatus(), "")
                + "; readyForBlockingApproval=" + command.readyForBlockingApproval()
                + "; missingEvidence=" + joinList(command.missingEvidence())
                + "; missingCapabilities=" + joinList(command.missingCapabilities())
                + "; sourceSummary=" + blankToDefault(command.sourceSummary(), "")
                + "; boundary=pre-approval handoff task, not final IAM approval";
    }

    private String preApprovalSeverity(AccountPreApprovalTaskCommand command) {
        if (command.readyForBlockingApproval()) {
            return "amber";
        }
        String riskLevel = blankToDefault(command.riskLevel(), "");
        if (riskLevel.contains("高") || "HIGH".equalsIgnoreCase(riskLevel)) {
            return "red";
        }
        return "amber";
    }

    private String nextAccountApprovalCode() {
        return "AUTH-ACC-APP-" + OffsetDateTime.now().format(ACCOUNT_APPROVAL_CODE_TIME)
                + "-" + String.format("%04d", accountApprovalSequence.incrementAndGet());
    }

    private String nextAccountPreApprovalCode() {
        return "AUTH-PRE-TASK-" + OffsetDateTime.now().format(ACCOUNT_APPROVAL_CODE_TIME)
                + "-" + String.format("%04d", accountPreApprovalSequence.incrementAndGet());
    }

    private String firstOrDefault(List<String> values, String fallback) {
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return fallback;
        }
        return values.get(0).trim();
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("|", values);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }
}
