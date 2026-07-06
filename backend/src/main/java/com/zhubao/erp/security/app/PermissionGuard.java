package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PermissionGuard {

    private final Map<PermissionCode, Set<RoleCode>> roleMatrix = new EnumMap<>(PermissionCode.class);

    public PermissionGuard() {
        allow(PermissionCode.INVENTORY_LEDGER_TRANSFER, RoleCode.ADMIN);
        allow(PermissionCode.BUSINESS_INBOUND_CONFIRM, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER);
        allow(PermissionCode.BUSINESS_PRODUCTION_REPORT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_QUALITY_FREEZE, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_REWORK_DISPATCH, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_REWORK_REPORT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_QUALITY_RELEASE, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_OUTBOUND_SHIPMENT, RoleCode.WAREHOUSE_KEEPER, RoleCode.SALES);
        allow(PermissionCode.BUSINESS_CUSTOMER_RETURN, RoleCode.WAREHOUSE_KEEPER, RoleCode.SALES, RoleCode.QUALITY_INSPECTOR);
        allow(PermissionCode.BUSINESS_AFTER_SALES_REVIEW, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.BUSINESS_LOSS_APPROVAL, RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER);
        allow(PermissionCode.APPROVAL_DECISION, RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.VIDEO_TASK_OPERATE, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
    }

    public void assertAllowed(PermissionCode permission, OperatorContext operatorContext) {
        if (operatorContext.role() == RoleCode.ADMIN) {
            return;
        }
        Set<RoleCode> allowedRoles = roleMatrix.getOrDefault(permission, Set.of());
        if (!allowedRoles.contains(operatorContext.role())) {
            throw new PermissionDeniedException(
                    "角色 " + operatorContext.role() + " 无权执行 " + permission.label()
            );
        }
    }

    public Map<PermissionCode, Set<RoleCode>> roleMatrix() {
        return Map.copyOf(roleMatrix);
    }

    private void allow(PermissionCode permission, RoleCode firstRole, RoleCode... otherRoles) {
        EnumSet<RoleCode> roles = EnumSet.of(firstRole, otherRoles);
        roles.add(RoleCode.ADMIN);
        roles.add(RoleCode.GENERAL_MANAGER);
        roleMatrix.put(permission, roles);
    }
}
