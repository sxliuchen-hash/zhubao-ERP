package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.domain.RoleCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ApprovalService {


    private final ApprovalTaskRepository repository;
    private final BusinessDataAccess businessDataAccess;
    private final ApprovalAccess access = new ApprovalAccess();

    public ApprovalService(ApprovalTaskRepository repository) {
        this(repository, null);
    }

    @Autowired
    public ApprovalService(ApprovalTaskRepository repository, BusinessDataAccess businessDataAccess) {
        this.repository = repository;
        this.businessDataAccess = businessDataAccess;
    }

    public ApprovalTask createLossApprovalTask(LossApprovalResult result, com.zhubao.erp.work.domain.WorkSession session) {
        return repository.insert(lossTask(result), session);
    }

    private ApprovalTask lossTask(LossApprovalResult result) {
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
        return task;
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

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }
}
