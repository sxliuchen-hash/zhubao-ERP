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
    private static final String GET = "GET";

    private final OperatorContextResolver operatorContextResolver;
    private final PermissionGuard permissionGuard;
    private final Map<String, PermissionCode> exactPostPermissions = Map.ofEntries(
            Map.entry("/api/inventory/ledger/transfers", PermissionCode.INVENTORY_LEDGER_TRANSFER),
            Map.entry("/api/business/operations/inbound-confirmations", PermissionCode.BUSINESS_INBOUND_CONFIRM),
            Map.entry("/api/business/operations/production-reports", PermissionCode.BUSINESS_PRODUCTION_REPORT),
            Map.entry("/api/business/operations/process-stations", PermissionCode.BUSINESS_PRODUCTION_REPORT),
            Map.entry("/api/business/operations/quality-checks", PermissionCode.BUSINESS_QUALITY_FREEZE),
            Map.entry("/api/business/operations/quality-checks/rework", PermissionCode.BUSINESS_REWORK_DISPATCH),
            Map.entry("/api/business/operations/quality-checks/rework-reports", PermissionCode.BUSINESS_REWORK_REPORT),
            Map.entry("/api/business/operations/quality-checks/rechecks", PermissionCode.BUSINESS_QUALITY_FREEZE),
            Map.entry("/api/business/operations/quality-checks/releases", PermissionCode.BUSINESS_QUALITY_RELEASE),
            Map.entry("/api/business/operations/quality-freezes", PermissionCode.BUSINESS_QUALITY_FREEZE),
            Map.entry("/api/business/operations/rework-dispatches", PermissionCode.BUSINESS_REWORK_DISPATCH),
            Map.entry("/api/business/operations/rework-reports", PermissionCode.BUSINESS_REWORK_REPORT),
            Map.entry("/api/business/operations/quality-releases", PermissionCode.BUSINESS_QUALITY_RELEASE),
            Map.entry("/api/business/operations/outbound-shipments", PermissionCode.BUSINESS_OUTBOUND_SHIPMENT),
            Map.entry("/api/business/operations/customer-returns", PermissionCode.BUSINESS_CUSTOMER_RETURN),
            Map.entry("/api/business/operations/after-sales-reviews", PermissionCode.BUSINESS_AFTER_SALES_REVIEW),
            Map.entry("/api/business/operations/loss-approvals", PermissionCode.BUSINESS_LOSS_APPROVAL),
            Map.entry("/api/machine-outputs", PermissionCode.MACHINE_OUTPUT_SUBMIT),
            Map.entry("/api/machine-outputs/machine-bindings", PermissionCode.MACHINE_OUTPUT_SUBMIT),
            Map.entry("/api/machine-profiles", PermissionCode.MACHINE_OUTPUT_SUBMIT),
            Map.entry("/api/hardware/gateway/consumptions", PermissionCode.GATEWAY_READING_CONSUME),
            Map.entry("/api/hardware/gateway/devices", PermissionCode.GATEWAY_DEVICE_MANAGE),
            Map.entry("/api/security/customer-authorizations", PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE),
            Map.entry("/api/security/customer-authorizations/revocations", PermissionCode.CUSTOMER_AUTHORIZATION_MANAGE),
            Map.entry("/api/auth/accounts", PermissionCode.AUTH_ACCOUNT_MANAGE),
            Map.entry("/api/documents/attachments/uploads", PermissionCode.DOCUMENT_ATTACHMENT_UPLOAD),
            Map.entry("/api/documents/captures", PermissionCode.DOCUMENT_CAPTURE),
            Map.entry("/api/documents/evidence-files", PermissionCode.DOCUMENT_CAPTURE),
            Map.entry("/api/documents/evidence-links", PermissionCode.DOCUMENT_CAPTURE),
            Map.entry("/api/business/operations/mobile-intakes", PermissionCode.BUSINESS_INBOUND_CONFIRM),
            Map.entry("/api/video-clip-tasks", PermissionCode.VIDEO_TASK_OPERATE),
            Map.entry("/api/label/generate", PermissionCode.LABEL_PRINT),
            Map.entry("/api/label/templates", PermissionCode.LABEL_PRINT),
            Map.entry("/api/label/reprints", PermissionCode.LABEL_PRINT)
    );
    private final Pattern motherSplitPath = Pattern.compile("^/api/business/operations/mother-orders/[^/]+/(keep|splits)$");
    private final Pattern documentRetirePath = Pattern.compile("^/api/documents/attachments/[^/]+/retirements$");
    private final Pattern approvalDecisionPath = Pattern.compile("^/api/approvals/tasks/[^/]+/decisions$");
    private final Pattern videoTaskOperationPath = Pattern.compile("^/api/video-clip-tasks/[^/]+/(retry|manual-upload)$");
    private final Pattern registerImportReviewPath = Pattern.compile("^/api/register-import/batches/[^/]+/rows/\\d+/corrections$");
    private final Pattern registerImportPostingPath = Pattern.compile("^/api/register-import/batches/[^/]+/posting-commands$");
    private final Pattern authAccountStatusPath = Pattern.compile("^/api/auth/accounts/[^/]+/(status|unlock|session-revocations)$");
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
        String path = request.getRequestURI();
        if (GET.equals(request.getMethod()) && path.startsWith("/api/finance/")) {
            return financeReadPermission(path);
        }
        if (!POST.equals(request.getMethod())) {
            return null;
        }
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
        if (motherSplitPath.matcher(path).matches()) {
            return PermissionCode.BUSINESS_MOTHER_SPLIT;
        }
        if (videoTaskOperationPath.matcher(path).matches()) {
            return PermissionCode.VIDEO_TASK_OPERATE;
        }
        if (documentRetirePath.matcher(path).matches()) {
            return PermissionCode.DOCUMENT_CONTENT_MANAGE;
        }
        if (path.startsWith("/api/finance/")) {
            return financePermission(path);
        }
        return null;
    }

    private PermissionCode financePermission(String path) {
        if (path.equals("/api/finance/price-agreements")) {
            return PermissionCode.FINANCE_PRICE_MANAGE;
        }
        if (path.equals("/api/finance/billing-runs") || path.equals("/api/finance/receivable-reversals")) {
            return PermissionCode.FINANCE_BILLING_POST;
        }
        if (path.equals("/api/finance/receipts")) {
            return PermissionCode.FINANCE_RECEIPT;
        }
        if (path.equals("/api/finance/worker-rates")
                || path.equals("/api/finance/payroll-runs")
                || path.equals("/api/finance/payroll-runs/confirmations")) {
            return PermissionCode.FINANCE_PAYROLL;
        }
        if (path.equals("/api/finance/consumable-items")
                || path.equals("/api/finance/consumable-purchases")
                || path.equals("/api/finance/consumable-issues")) {
            return PermissionCode.FINANCE_CONSUMABLE;
        }
        if (path.equals("/api/finance/fixed-assets") || path.equals("/api/finance/depreciation-runs")) {
            return PermissionCode.FINANCE_ASSET;
        }
        if (path.equals("/api/finance/outsource-bills") || path.equals("/api/finance/payable-payments")) {
            return PermissionCode.FINANCE_PAYABLE;
        }
        return null;
    }

    /** 财务读取单独授权。能看订单不代表能看收入、工资或公司成本。 */
    private PermissionCode financeReadPermission(String path) {
        if (path.equals("/api/finance/statements")
                || path.equals("/api/finance/price-agreements")
                || path.equals("/api/finance/unpriced")
                || path.equals("/api/finance/operating-statement")
                || path.equals("/api/finance/dashboard")
                || path.equals("/api/finance/dashboard/details")) {
            return PermissionCode.FINANCE_CUSTOMER_READ;
        }
        return PermissionCode.FINANCE_COMPANY_READ;
    }
}
