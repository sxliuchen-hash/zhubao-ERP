package com.zhubao.erp.approval.app;

import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalStatus;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 按任务场景判断可见范围和允许的审批身份。
 * 账号仍由管理员直接保存；本类只约束审批记录的查看和确认，不把历史前置申请改成强制账号审批。
 */
public class ApprovalAccess {

    public static final String LOSS = "LOSS_APPROVAL";
    public static final String ACCOUNT_MANAGEMENT = "ACCOUNT_MANAGEMENT";
    public static final String ACCOUNT_PRE_APPROVAL = "ACCOUNT_PRE_APPROVAL";

    public boolean canRead(ApprovalActor actor, ApprovalTask task) {
        if (actor == null || actor.role() == null || task == null) {
            return false;
        }
        if (actor.role() == RoleCode.ADMIN || samePerson(actor, task.applicant())) {
            return true;
        }
        return allowedRoles(task).contains(actor.role());
    }

    public boolean canDecide(ApprovalActor actor, ApprovalTask task) {
        if (actor == null || actor.role() == null || actor.operator().isBlank() || task == null) {
            return false;
        }
        if (task.status() != ApprovalStatus.WAITING_APPROVAL || samePerson(actor, task.applicant())) {
            return false;
        }
        return allowedRoles(task).contains(actor.role());
    }

    public void assertCanDecide(ApprovalActor actor, ApprovalTask task) {
        if (actor == null || actor.role() == null || actor.operator().isBlank()) {
            throw new PermissionDeniedException("请先登录后再审批");
        }
        if (task.status() != ApprovalStatus.WAITING_APPROVAL) {
            throw new BusinessException("该审批已处理，不能重复决策");
        }
        if (samePerson(actor, task.applicant())) {
            throw new PermissionDeniedException("申请人不能审批自己提交的任务");
        }
        if (!allowedRoles(task).contains(actor.role())) {
            throw new PermissionDeniedException("当前身份不能审批该场景");
        }
    }

    private Set<RoleCode> allowedRoles(ApprovalTask task) {
        String scene = task.scene() == null ? "" : task.scene().trim();
        return switch (scene) {
            case LOSS -> EnumSet.of(RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER, RoleCode.ADMIN);
            case ACCOUNT_MANAGEMENT -> EnumSet.of(RoleCode.GENERAL_MANAGER, RoleCode.ADMIN);
            case ACCOUNT_PRE_APPROVAL -> preApprovalRoles(task);
            default -> EnumSet.noneOf(RoleCode.class);
        };
    }

    private Set<RoleCode> preApprovalRoles(ApprovalTask task) {
        EnumSet<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        addRole(roles, task.ownerRole());
        String raw = task.approver() == null ? "" : task.approver();
        for (String token : raw.split("[|,，;；\\s]+")) {
            addRole(roles, token);
        }
        return roles;
    }

    private void addRole(Set<RoleCode> roles, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            roles.add(RoleCode.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // 中文责任人名称不是角色编码，不能据此放行。
        }
    }

    private boolean samePerson(ApprovalActor actor, String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return false;
        }
        String value = snapshot.trim();
        if (!actor.operator().isEmpty() && value.equals(actor.operator())) {
            return true;
        }
        return !actor.username().isEmpty() && value.equalsIgnoreCase(actor.username());
    }
}
