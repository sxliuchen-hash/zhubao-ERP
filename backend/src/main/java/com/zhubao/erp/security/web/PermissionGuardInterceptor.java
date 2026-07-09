package com.zhubao.erp.security.web;

import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.PermissionCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PermissionGuardInterceptor implements HandlerInterceptor {

    private static final String POST = "POST";

    private final OperatorContextResolver operatorContextResolver;
    private final PermissionGuard permissionGuard;
    private final Map<String, PermissionCode> exactPostPermissions = Map.ofEntries(
            Map.entry("/api/inventory/ledger/transfers", PermissionCode.INVENTORY_LEDGER_TRANSFER),
            Map.entry("/api/business/operations/inbound-confirmations", PermissionCode.BUSINESS_INBOUND_CONFIRM),
            Map.entry("/api/business/operations/production-reports", PermissionCode.BUSINESS_PRODUCTION_REPORT),
            Map.entry("/api/business/operations/quality-freezes", PermissionCode.BUSINESS_QUALITY_FREEZE),
            Map.entry("/api/business/operations/rework-dispatches", PermissionCode.BUSINESS_REWORK_DISPATCH),
            Map.entry("/api/business/operations/rework-reports", PermissionCode.BUSINESS_REWORK_REPORT),
            Map.entry("/api/business/operations/quality-releases", PermissionCode.BUSINESS_QUALITY_RELEASE),
            Map.entry("/api/business/operations/outbound-shipments", PermissionCode.BUSINESS_OUTBOUND_SHIPMENT),
            Map.entry("/api/business/operations/customer-returns", PermissionCode.BUSINESS_CUSTOMER_RETURN),
            Map.entry("/api/business/operations/after-sales-reviews", PermissionCode.BUSINESS_AFTER_SALES_REVIEW),
            Map.entry("/api/business/operations/loss-approvals", PermissionCode.BUSINESS_LOSS_APPROVAL),
            Map.entry("/api/machine-outputs", PermissionCode.MACHINE_OUTPUT_SUBMIT),
            Map.entry("/api/hardware/gateway/consumptions", PermissionCode.GATEWAY_READING_CONSUME),
            Map.entry("/api/hardware/gateway/devices", PermissionCode.GATEWAY_DEVICE_MANAGE),
            Map.entry("/api/security/customer-authorizations", PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE),
            Map.entry("/api/security/customer-authorizations/revocations", PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE),
            Map.entry("/api/auth/accounts", PermissionCode.AUTH_ACCOUNT_MANAGE),
            Map.entry("/api/documents/attachments/uploads", PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD),
            Map.entry("/api/video-clip-tasks", PermissionCode.VIDEO_TASK_OPERATE)
    );
    private final Pattern approvalDecisionPath = Pattern.compile("^/api/approvals/tasks/[^/]+/decisions$");
    private final Pattern videoTaskOperationPath = Pattern.compile("^/api/video-clip-tasks/[^/]+/(retry|manual-upload)$");
    private final Pattern registerImportReviewPath = Pattern.compile("^/api/register-import/batches/[^/]+/rows/\\d+/corrections$");
    private final Pattern registerImportPostingPath = Pattern.compile("^/api/register-import/batches/[^/]+/posting-commands$");
    private final Pattern authAccountStatusPath = Pattern.compile("^/api/auth/accounts/[^/]+/(status|unlock)$");
    private final Pattern gatewayDeviceManagePath = Pattern.compile("^/api/hardware/gateway/devices/[^/]+/(status|rotate-key)$");

    public PermissionGuardInterceptor(
            OperatorContextResolver operatorContextResolver,
            PermissionGuard permissionGuard
    ) {
        this.operatorContextResolver = operatorContextResolver;
        this.permissionGuard = permissionGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PermissionCode permission = permissionFor(request);
        if (permission != null) {
            permissionGuard.assertAllowed(permission, operatorContextResolver.resolve(request));
        }
        return true;
    }

    private PermissionCode permissionFor(HttpServletRequest request) {
        if (!POST.equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        PermissionCode exactPermission = exactPostPermissions.get(path);
        if (exactPermission != null) {
            return exactPermission;
        }
        if (approvalDecisionPath.matcher(path).matches()) {
            return PermissionCode.APPROVAL_DECISION;
        }
        if (registerImportReviewPath.matcher(path).matches()) {
            return PermissionCode.REGISTER_IMPORT_REVIEW;
        }
        if (registerImportPostingPath.matcher(path).matches()) {
            return PermissionCode.REGISTER_IMPORT_POSTING;
        }
        if (authAccountStatusPath.matcher(path).matches()) {
            return PermissionCode.AUTH_ACCOUNT_MANAGE;
        }
        if (gatewayDeviceManagePath.matcher(path).matches()) {
            return PermissionCode.GATEWAY_DEVICE_MANAGE;
        }
        if (videoTaskOperationPath.matcher(path).matches()) {
            return PermissionCode.VIDEO_TASK_OPERATE;
        }
        return null;
    }
}
