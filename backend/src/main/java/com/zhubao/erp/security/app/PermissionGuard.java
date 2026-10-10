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
import java.util.stream.Collectors;

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
        allow(PermissionCode.REGISTER_IMPORT_REVIEW, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.REGISTER_IMPORT_POSTING, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.MACHINE_OUTPUT_SUBMIT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.GATEWAY_READING_CONSUME, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER, RoleCode.QUALITY_INSPECTOR, RoleCode.WAREHOUSE_KEEPER, RoleCode.RECEIVER);
        allow(PermissionCode.GATEWAY_DEVICE_MANAGE, RoleCode.ADMIN);
        allow(PermissionCode.AUTH_ACCOUNT_MANAGE, RoleCode.GENERAL_MANAGER);
        allow(PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE, RoleCode.GENERAL_MANAGER);
        allow(PermissionCode.APPROVAL_DECISION, RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.DOCUMENT_CONTENT_MANAGE, RoleCode.ADMIN);
        allow(PermissionCode.DOCUMENT_CAPTURE, RoleCode.OPERATOR, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.VIDEO_TASK_OPERATE, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.LABEL_PRINT, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER, RoleCode.OPERATOR);
        allow(PermissionCode.BUSINESS_MOTHER_SPLIT, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.FINANCE_PRICE_MANAGE, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_BILLING_POST, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_RECEIPT, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_PAYROLL, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_CONSUMABLE, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_ASSET, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_PAYABLE, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_CUSTOMER_READ, RoleCode.FINANCE_STATISTICIAN);
        allow(PermissionCode.FINANCE_COMPANY_READ, RoleCode.FINANCE_STATISTICIAN);
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

    public Set<PermissionCode> allowedPermissionsFor(RoleCode role) {
        if (role == RoleCode.ADMIN) {
            return EnumSet.allOf(PermissionCode.class);
        }
        return roleMatrix.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(role))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionCode.class)));
    }

    private void allow(PermissionCode permission, RoleCode firstRole, RoleCode... otherRoles) {
        EnumSet<RoleCode> roles = EnumSet.of(firstRole, otherRoles);
        roles.add(RoleCode.ADMIN);
        roles.add(RoleCode.GENERAL_MANAGER);
        roleMatrix.put(permission, roles);
    }
}
