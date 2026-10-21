package com.zhubao.erp.approval.web;

import com.zhubao.erp.approval.app.ApprovalService;
import com.zhubao.erp.approval.domain.ApprovalActor;
import com.zhubao.erp.approval.domain.ApprovalDecisionCommand;
import com.zhubao.erp.approval.domain.ApprovalTask;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final AuthSessionService authSessionService;

    public ApprovalController(ApprovalService approvalService, AuthSessionService authSessionService) {
        this.approvalService = approvalService;
        this.authSessionService = authSessionService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ApprovalTask>> tasks(HttpServletRequest request) {
        return ApiResponse.ok(approvalService.visibleTasks(actor(request)));
    }

    @GetMapping("/tasks/pending")
    public ApiResponse<List<ApprovalTask>> pendingTasks(HttpServletRequest request) {
        return ApiResponse.ok(approvalService.pendingTasks(actor(request)));
    }

    @GetMapping("/tasks/{approvalCode}")
    public ApiResponse<ApprovalTask> task(@PathVariable String approvalCode, HttpServletRequest request) {
        return ApiResponse.ok(approvalService.visibleTask(approvalCode, actor(request)));
    }

    @PostMapping("/tasks/{approvalCode}/decisions")
    public ApiResponse<ApprovalTask> decide(
            @PathVariable String approvalCode,
            @Valid @RequestBody ApprovalDecisionCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(approvalService.decide(approvalCode, command, actor(request)));
    }

    private ApprovalActor actor(HttpServletRequest request) {
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("请先登录后再查看审批"));
        if (account.operator() == null || account.operator().isBlank() || account.role() == null) {
            throw new PermissionDeniedException("请先登录后再查看审批");
        }
        return new ApprovalActor(account.operator(), account.username(), account.role());
    }
}
