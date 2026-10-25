package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ProcessingIntakePendingService;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.*;
import com.zhubao.erp.security.domain.PermissionCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/business/operations/intake-pendings")
public class ProcessingIntakePendingController {
    private final ProcessingIntakePendingService service; private final AuthSessionService sessions;
    private final OperatorContextResolver operators; private final PermissionGuard permissions;
    public ProcessingIntakePendingController(ProcessingIntakePendingService service, AuthSessionService sessions, OperatorContextResolver operators, PermissionGuard permissions) { this.service = service; this.sessions = sessions; this.operators = operators; this.permissions = permissions; }
    private String account(HttpServletRequest request) { return sessions.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER)).orElseThrow(() -> new com.zhubao.erp.common.error.BusinessException("请先登录")).username(); }
    @GetMapping public ApiResponse<List<ProcessingIntakePending>> list(HttpServletRequest request) {
        permissions.assertAllowed(PermissionCode.BUSINESS_INBOUND_CONFIRM, operators.requireSession(request)); return ApiResponse.ok(service.list(account(request), operators.requireSession(request)));
    }
    public record Save(String code, int expectedVersion, String requestId, ProcessingIntakeDraft draft) { }
    @PostMapping public ApiResponse<ProcessingIntakePending> save(@RequestBody Save command, HttpServletRequest request) {
        var operator = operators.requireSession(request); permissions.assertAllowed(PermissionCode.BUSINESS_INBOUND_CONFIRM, operator);
        return ApiResponse.ok(service.save(command.code(), command.expectedVersion(), command.requestId(), command.draft(), account(request), operator));
    }
}
