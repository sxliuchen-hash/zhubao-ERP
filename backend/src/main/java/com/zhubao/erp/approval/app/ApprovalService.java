package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.approval.domain.ApprovalTaskRepository;
import com.zhubao.erp.business.domain.ApproveLossCommand;
import com.zhubao.erp.business.domain.LossApprovalResult;
import com.zhubao.erp.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ApprovalService {

    private final ApprovalTaskRepository repository;

    public ApprovalService(ApprovalTaskRepository repository) {
        this.repository = repository;
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

    public List<ApprovalTask> findAll() {
        return repository.findAll()
                .stream()
                .sorted(Comparator.comparing(ApprovalTask::createdAt).reversed())
                .toList();
    }

    public List<ApprovalTask> findPending() {
        return findAll()
                .stream()
                .filter(task -> task.status() == ApprovalStatus.WAITING_APPROVAL)
                .toList();
    }

    public ApprovalTask findByCode(String approvalCode) {
        return repository.findByCode(approvalCode)
                .orElseThrow(() -> new BusinessException(404, "Approval task not found: " + approvalCode));
    }

    public ApprovalTask decide(String approvalCode, ApprovalDecisionCommand command) {
        ApprovalTask current = findByCode(approvalCode);
        if (current.status() != ApprovalStatus.WAITING_APPROVAL) {
            throw new BusinessException("Approval task already decided: " + approvalCode);
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
                command.operator(),
                command.comment(),
                true
        );
        return repository.save(decided);
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
                + "; trialWage=" + result.trialWage()
                + "; formalPayroll=" + result.formalPayroll();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }
}
