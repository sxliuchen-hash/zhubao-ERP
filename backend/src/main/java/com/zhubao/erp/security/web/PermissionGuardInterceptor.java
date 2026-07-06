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
            Map.entry("/api/documents/attachments/uploads", PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD),
            Map.entry("/api/video-clip-tasks", PermissionCode.VIDEO_TASK_OPERATE)
    );
    private final Pattern approvalDecisionPath = Pattern.compile("^/api/approvals/tasks/[^/]+/decisions$");
    private final Pattern videoTaskOperationPath = Pattern.compile("^/api/video-clip-tasks/[^/]+/(retry|manual-upload)$");

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
        if (videoTaskOperationPath.matcher(path).matches()) {
            return PermissionCode.VIDEO_TASK_OPERATE;
        }
        return null;
    }
}
