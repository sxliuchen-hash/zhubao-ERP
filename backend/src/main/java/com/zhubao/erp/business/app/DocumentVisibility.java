package com.zhubao.erp.business.app;

import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.CustomerScopeRules;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;

import java.util.List;

/**
 * 单据可见性。客户名单为空、班组或部门未配置时，不把范围放大到全公司。
 */
public final class DocumentVisibility {

    private DocumentVisibility() {
    }

    public static boolean canSee(OperatorContext operator, Long customerId, String customerName, String area,
                                 List<String> participants, List<AuthUserAccount> directory) {
        if (operator == null || operator.role() == null) {
            return false;
        }
        if (operator.dataScope() == DataScope.ALL) {
            return true;
        }
        if (!customerConstraint(operator, customerId, customerName)) {
            return false;
        }
        if (operator.dataScope() == DataScope.CUSTOMER_RANGE) {
            return true;
        }
        if (personallyRelated(operator, participants)) {
            return true;
        }
        return orgArea(operator, area, participants, directory);
    }

    public static boolean canSeeAnyWorkplace(OperatorContext operator, Long customerId, String customerName,
                                             List<String> participants, List<AuthUserAccount> directory) {
        if (canSee(operator, customerId, customerName, "inbound", participants, directory)) {
            return true;
        }
        return canSee(operator, customerId, customerName, "production", participants, directory)
                || canSee(operator, customerId, customerName, "quality", participants, directory)
                || canSee(operator, customerId, customerName, "inventory", participants, directory);
    }

    private static boolean customerConstraint(OperatorContext operator, Long customerId, String customerName) {
        if (operator.customerScope().isEmpty()) {
            return operator.dataScope() != DataScope.CUSTOMER_RANGE;
        }
        return CustomerScopeRules.allowed(operator, customerId, customerName);
    }

    private static boolean personallyRelated(OperatorContext operator, List<String> participants) {
        String name = text(operator.operator());
        String username = text(operator.username());
        if (name.isEmpty() && username.isEmpty()) {
            return false;
        }
        if (participants == null) {
            return false;
        }
        for (String participant : participants) {
            String value = text(participant);
            if (!value.isEmpty() && (value.equals(name) || value.equalsIgnoreCase(username))) {
                return true;
            }
        }
        return false;
    }

    private static boolean orgArea(OperatorContext operator, String area, List<String> participants,
                                   List<AuthUserAccount> directory) {
        if (!roleMayUseOrg(operator.role()) || !roleCovers(operator.role(), area)) {
            return false;
        }
        String orgCode = switch (operator.dataScope()) {
            case TEAM -> operator.teamCode();
            case DEPARTMENT -> operator.departmentCode();
            default -> "";
        };
        if (orgCode.isBlank() || directory == null || directory.isEmpty()) {
            return false;
        }
        if (participants == null) {
            return false;
        }
        for (String participant : participants) {
            String value = text(participant);
            if (value.isEmpty()) {
                continue;
            }
            for (AuthUserAccount member : directory) {
                if (member == null || !sameOrg(operator, member, orgCode)) {
                    continue;
                }
                if (value.equals(text(member.operator())) || value.equalsIgnoreCase(text(member.username()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sameOrg(OperatorContext operator, AuthUserAccount member, String orgCode) {
        return operator.dataScope() == DataScope.TEAM
                ? orgCode.equals(member.teamCode())
                : orgCode.equals(member.departmentCode());
    }

    private static boolean roleMayUseOrg(RoleCode role) {
        return role == RoleCode.RECEIVER
                || role == RoleCode.WAREHOUSE_KEEPER
                || role == RoleCode.WORKSHOP_LEADER
                || role == RoleCode.QUALITY_INSPECTOR
                || role == RoleCode.BUSINESS_MANAGER
                || role == RoleCode.ADMIN
                || role == RoleCode.GENERAL_MANAGER;
    }

    private static boolean roleCovers(RoleCode role, String area) {
        return switch (area == null ? "" : area) {
            case "inbound", "inventory" -> role == RoleCode.RECEIVER || role == RoleCode.WAREHOUSE_KEEPER
                    || role == RoleCode.BUSINESS_MANAGER || role == RoleCode.ADMIN || role == RoleCode.GENERAL_MANAGER;
            case "production" -> role == RoleCode.WORKSHOP_LEADER || role == RoleCode.BUSINESS_MANAGER
                    || role == RoleCode.ADMIN || role == RoleCode.GENERAL_MANAGER;
            case "quality" -> role == RoleCode.QUALITY_INSPECTOR || role == RoleCode.BUSINESS_MANAGER
                    || role == RoleCode.ADMIN || role == RoleCode.GENERAL_MANAGER;
            default -> false;
        };
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
