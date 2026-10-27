package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.CoverDuties;
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
        allowManager(PermissionCode.BUSINESS_INBOUND_CONFIRM, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER);
        allowManager(PermissionCode.BUSINESS_PRODUCTION_REPORT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_QUALITY_FREEZE, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_REWORK_DISPATCH, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_REWORK_REPORT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_QUALITY_RELEASE, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_OUTBOUND_SHIPMENT, RoleCode.WAREHOUSE_KEEPER, RoleCode.SALES);
        allowManager(PermissionCode.BUSINESS_CUSTOMER_RETURN, RoleCode.WAREHOUSE_KEEPER, RoleCode.SALES, RoleCode.QUALITY_INSPECTOR);
        allowManager(PermissionCode.BUSINESS_AFTER_SALES_REVIEW, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.BUSINESS_LOSS_APPROVAL, RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER);
        allowManager(PermissionCode.REGISTER_IMPORT_REVIEW, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.REGISTER_IMPORT_POSTING, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.MACHINE_OUTPUT_SUBMIT, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.GATEWAY_READING_CONSUME, RoleCode.OPERATOR, RoleCode.WORKSHOP_LEADER, RoleCode.QUALITY_INSPECTOR, RoleCode.WAREHOUSE_KEEPER, RoleCode.RECEIVER);
        allow(PermissionCode.GATEWAY_DEVICE_MANAGE, RoleCode.ADMIN);
        roleMatrix.put(PermissionCode.AUTH_ACCOUNT_MANAGE, EnumSet.of(RoleCode.ADMIN));
        roleMatrix.put(PermissionCode.ORDER_ADMIN_MAINTAIN, EnumSet.of(RoleCode.ADMIN));
        roleMatrix.put(PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE, EnumSet.of(RoleCode.ADMIN));
        allowManager(PermissionCode.MASTER_DATA_MANAGE, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.APPROVAL_DECISION, RoleCode.WORKSHOP_LEADER, RoleCode.GENERAL_MANAGER, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allow(PermissionCode.DOCUMENT_CONTENT_MANAGE, RoleCode.ADMIN);
        allowManager(PermissionCode.DOCUMENT_CAPTURE, RoleCode.OPERATOR, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.QUALITY_INSPECTOR, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.VIDEO_TASK_OPERATE, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.LABEL_PRINT, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER, RoleCode.OPERATOR);
        allowManager(PermissionCode.BUSINESS_MOTHER_SPLIT, RoleCode.RECEIVER, RoleCode.WAREHOUSE_KEEPER, RoleCode.WORKSHOP_LEADER);
        allowManager(PermissionCode.FINANCE_PRICE_MANAGE, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_BILLING_POST, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_RECEIPT, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_PAYROLL, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_CONSUMABLE, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_ASSET, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_PAYABLE, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_CUSTOMER_READ, RoleCode.FINANCE_STATISTICIAN);
        allowManager(PermissionCode.FINANCE_COMPANY_READ, RoleCode.FINANCE_STATISTICIAN);
        // 业务管理可分给多人，只顶上收货、报工和质检，不带财务入账、发货和账号管理。
        grant(PermissionCode.BUSINESS_INBOUND_CONFIRM, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_PRODUCTION_REPORT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_REWORK_REPORT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_QUALITY_FREEZE, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_REWORK_DISPATCH, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_QUALITY_RELEASE, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_AFTER_SALES_REVIEW, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_CUSTOMER_RETURN, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.REGISTER_IMPORT_REVIEW, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.REGISTER_IMPORT_POSTING, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.MACHINE_OUTPUT_SUBMIT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.GATEWAY_READING_CONSUME, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.MASTER_DATA_MANAGE, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.DOCUMENT_CAPTURE, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.LABEL_PRINT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_MOTHER_SPLIT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_OUTBOUND_SHIPMENT, RoleCode.BUSINESS_MANAGER);
        grant(PermissionCode.BUSINESS_LOSS_APPROVAL, RoleCode.BUSINESS_MANAGER);
        allowManager(PermissionCode.ORDER_CHANGE_REVIEW, RoleCode.WORKSHOP_LEADER);
        grant(PermissionCode.ORDER_CHANGE_REVIEW, RoleCode.BUSINESS_MANAGER);
        roleMatrix.put(PermissionCode.STOCK_COMPANY_RECEIVE, EnumSet.of(RoleCode.WAREHOUSE_KEEPER));
        roleMatrix.put(PermissionCode.STOCK_COMPANY_ISSUE, EnumSet.of(RoleCode.WAREHOUSE_KEEPER));
        roleMatrix.put(PermissionCode.STOCK_COMPANY_RETURN, EnumSet.of(RoleCode.WAREHOUSE_KEEPER));
        roleMatrix.put(PermissionCode.STOCK_PREP_RECEIVE, EnumSet.of(RoleCode.WAREHOUSE_KEEPER, RoleCode.RECEIVER));
        roleMatrix.put(PermissionCode.STOCK_PREP_ISSUE, EnumSet.of(RoleCode.WAREHOUSE_KEEPER, RoleCode.RECEIVER));
        roleMatrix.put(PermissionCode.STOCK_PREP_RETURN, EnumSet.of(RoleCode.WAREHOUSE_KEEPER, RoleCode.RECEIVER));
        roleMatrix.put(PermissionCode.STOCK_PREP_CUSTOMER_RETURN, EnumSet.of(RoleCode.WAREHOUSE_KEEPER));
        roleMatrix.put(PermissionCode.STOCK_FREEZE, EnumSet.of(RoleCode.WAREHOUSE_KEEPER));
        roleMatrix.put(PermissionCode.STOCK_CORRECT, EnumSet.of(RoleCode.ADMIN));
        // 待办指派不自动交给总经理。业务管理只要已有任一顶岗即可指派。
        allow(PermissionCode.DESK_TASK_ASSIGN, RoleCode.WORKSHOP_LEADER, RoleCode.SALES);
        grant(PermissionCode.DESK_TASK_ASSIGN, RoleCode.BUSINESS_MANAGER);
    }

    public void assertAllowed(PermissionCode permission, OperatorContext operatorContext) {
        if (operatorContext.role() == RoleCode.ADMIN) {
            return;
        }
        Set<RoleCode> allowedRoles = roleMatrix.getOrDefault(permission, Set.of());
        if (!allowedRoles.contains(operatorContext.role())
                || operatorContext.role() == RoleCode.BUSINESS_MANAGER && !coverAllows(permission, operatorContext.coverDuties())) {
            throw new PermissionDeniedException(
                    "角色 " + operatorContext.role() + " 无权执行 " + permission.label()
            );
        }
    }

    public Map<PermissionCode, Set<RoleCode>> roleMatrix() {
        return Map.copyOf(roleMatrix);
    }

    public Set<PermissionCode> effectivePermissions(RoleCode role, java.util.List<String> coverDuties) {
        Set<PermissionCode> permissions = allowedPermissionsFor(role);
        if (role != RoleCode.BUSINESS_MANAGER) {
            return permissions;
        }
        return permissions.stream()
                .filter(permission -> coverAllows(permission, coverDuties))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionCode.class)));
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
        roleMatrix.put(permission, roles);
    }

    /** 总经理继续保留已经在用的日常权限，不再因为登记动作而自动获得新权限。 */
    private void allowManager(PermissionCode permission, RoleCode firstRole, RoleCode... otherRoles) {
        allow(permission, firstRole, otherRoles);
        grant(permission, RoleCode.GENERAL_MANAGER);
    }

    private static boolean coverAllows(PermissionCode permission, java.util.List<String> duties) {
        String duty = switch (permission) {
            case BUSINESS_INBOUND_CONFIRM -> CoverDuties.INTAKE;
            case BUSINESS_PRODUCTION_REPORT, BUSINESS_REWORK_REPORT, MACHINE_OUTPUT_SUBMIT -> CoverDuties.REPORT;
            case BUSINESS_QUALITY_FREEZE, BUSINESS_REWORK_DISPATCH, BUSINESS_QUALITY_RELEASE,
                    BUSINESS_AFTER_SALES_REVIEW -> CoverDuties.QUALITY;
            case BUSINESS_MOTHER_SPLIT -> CoverDuties.SPLIT;
            case LABEL_PRINT -> CoverDuties.LABEL;
            case MASTER_DATA_MANAGE -> CoverDuties.MASTER;
            case BUSINESS_OUTBOUND_SHIPMENT -> CoverDuties.SHIP;
            case BUSINESS_CUSTOMER_RETURN -> CoverDuties.RETURN;
            case REGISTER_IMPORT_REVIEW, REGISTER_IMPORT_POSTING -> CoverDuties.REGISTER;
            case BUSINESS_LOSS_APPROVAL -> CoverDuties.LOSS;
            case ORDER_CHANGE_REVIEW -> null;
            case DESK_TASK_ASSIGN, GATEWAY_READING_CONSUME, DOCUMENT_ATTACHMENT_UPLOAD, DOCUMENT_CAPTURE -> null;
            case STOCK_COMPANY_RECEIVE, STOCK_COMPANY_ISSUE, STOCK_COMPANY_RETURN, STOCK_PREP_RECEIVE,
                    STOCK_PREP_ISSUE, STOCK_PREP_RETURN, STOCK_PREP_CUSTOMER_RETURN, STOCK_FREEZE, STOCK_CORRECT -> "";
            default -> "";
        };
        if (duty == null) {
            return duties != null && !duties.isEmpty();
        }
        return !duty.isEmpty() && CoverDuties.contains(duties, duty);
    }

    private void grant(PermissionCode permission, RoleCode role) {
        roleMatrix.computeIfAbsent(permission, ignored -> EnumSet.noneOf(RoleCode.class)).add(role);
    }
}
