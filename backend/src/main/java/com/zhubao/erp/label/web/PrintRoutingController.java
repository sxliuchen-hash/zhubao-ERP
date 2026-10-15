package com.zhubao.erp.label.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.app.PrintBatchService;
import com.zhubao.erp.label.domain.PrintBatch;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTarget;
import com.zhubao.erp.label.domain.PrintTargetJob;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/label")
public class PrintRoutingController {
    private final PrintBatchService service;
    private final OperatorContextResolver operators;
    private final AuthSessionService auth;
    private final PermissionGuard permissions;
    public PrintRoutingController(PrintBatchService service, OperatorContextResolver operators,
                                  AuthSessionService auth, PermissionGuard permissions) {
        this.service = service; this.operators = operators; this.auth = auth; this.permissions = permissions;
    }
    @GetMapping("/print-stations")
    public ApiResponse<List<PrintStation>> stations(HttpServletRequest request) {
        operators.requireSession(request); return ApiResponse.ok(service.stations());
    }
    @PostMapping("/print-stations")
    public ApiResponse<PrintBatchService.StationCredential> station(@RequestBody PrintStation station, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.saveStation(station));
    }
    @PostMapping("/print-stations/{id}/rotate-token")
    public ApiResponse<PrintBatchService.StationCredential> rotate(@PathVariable String id, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.rotate(id));
    }
    @GetMapping("/print-queues")
    public ApiResponse<List<PrintQueue>> queues(HttpServletRequest request) {
        operators.requireSession(request); return ApiResponse.ok(service.availableQueues());
    }
    @GetMapping("/print-queues/all")
    public ApiResponse<List<PrintQueue>> allQueues(HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.queues());
    }
    @PostMapping("/print-queues")
    public ApiResponse<PrintQueue> queue(@RequestBody PrintQueue queue, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, operators.requireSession(request));
        return ApiResponse.ok(service.saveQueue(queue));
    }
    @PostMapping("/print-batches")
    public ApiResponse<PrintBatch> create(@RequestBody CreateBatchRequest body, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.LABEL_PRINT, operators.requireSession(request));
        return ApiResponse.ok(service.create(body.requestId(), body.businessCode(), body.templateCode(),
                body.copiesPerTarget(), body.targets(), account(request)));
    }
    @GetMapping("/print-batches/{id}")
    public ApiResponse<PrintBatch> batch(@PathVariable String id, HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.LABEL_PRINT, operators.requireSession(request));
        return ApiResponse.ok(service.batch(id, account(request)));
    }
    @PostMapping("/print-target-jobs/{id}/paper-checks")
    public ApiResponse<PrintTargetJob> paper(@PathVariable String id, @RequestBody PaperCheck body,
                                             HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.LABEL_PRINT, operators.requireSession(request));
        return ApiResponse.ok(service.paperCheck(id, body.result(), account(request)));
    }
    @PostMapping("/agents/v2/jobs/claim")
    public ApiResponse<PrintBatchService.ClaimedPrintJob> claim(@RequestHeader("X-Print-Station") String station,
                                                                 @RequestHeader("X-Print-Token") String token) {
        return ApiResponse.ok(service.claim(station, token));
    }
    @PostMapping("/agents/v2/jobs/{id}/report")
    public ApiResponse<PrintTargetJob> report(@PathVariable String id,
                                               @RequestHeader("X-Print-Station") String station,
                                               @RequestHeader("X-Print-Token") String token,
                                               @RequestBody Report body) {
        return ApiResponse.ok(service.report(id, station, token, body.outcome(), body.failureReason()));
    }
    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operators.requireSession(request);
        AuthUserAccount current = auth.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再打印"));
        return new AuthUserAccount(current.username(), current.passwordHash(), current.operator(), current.role(),
                current.dataScope(), current.enabled(), current.demoAccount(), current.passwordChangedAt(),
                current.failedLoginCount(), current.lockedUntil(), context.customerScope(),
                current.passwordChangeRequired(), current.temporaryPasswordConsumed(), current.allowedProcesses());
    }
    public record CreateBatchRequest(String requestId, String businessCode, String templateCode,
                                     int copiesPerTarget, List<PrintTarget> targets) {}
    public record PaperCheck(String result) {}
    public record Report(String outcome, String failureReason) {}
}
