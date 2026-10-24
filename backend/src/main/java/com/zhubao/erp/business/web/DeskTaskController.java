package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.DeskTaskService;
import com.zhubao.erp.business.domain.CancelDeskTaskCommand;
import com.zhubao.erp.business.domain.CompleteDeskTaskCommand;
import com.zhubao.erp.business.domain.CreateDeskTaskCommand;
import com.zhubao.erp.business.domain.DeskAssignee;
import com.zhubao.erp.business.domain.DeskTask;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/desk-tasks")
public class DeskTaskController {

    private final DeskTaskService tasks;
    private final OperatorContextResolver operators;
    private final PermissionGuard permissions;
    private final AuthSessionService sessions;

    public DeskTaskController(DeskTaskService tasks, OperatorContextResolver operators, PermissionGuard permissions,
                              AuthSessionService sessions) {
        this.tasks = tasks;
        this.operators = operators;
        this.permissions = permissions;
        this.sessions = sessions;
    }

    @GetMapping
    public ApiResponse<List<DeskTask>> list(@RequestParam(required = false) String view, HttpServletRequest request) {
        operators.requireSession(request);
        return ApiResponse.ok(tasks.list(view, account(request)));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<DeskAssignee>> assignees(HttpServletRequest request) {
        operators.requireSession(request);
        permissions.assertAllowed(PermissionCode.DESK_TASK_ASSIGN, operators.requireSession(request));
        return ApiResponse.ok(tasks.assignees(account(request)));
    }

    @PostMapping
    public ApiResponse<DeskTask> create(@RequestBody CreateDeskTaskCommand command, HttpServletRequest request) {
        operators.requireSession(request);
        permissions.assertAllowed(PermissionCode.DESK_TASK_ASSIGN, operators.requireSession(request));
        return ApiResponse.ok(tasks.create(command, account(request)));
    }

    @PostMapping("/{taskCode}/completions")
    public ApiResponse<DeskTask> complete(@PathVariable String taskCode, @RequestBody CompleteDeskTaskCommand command,
                                          HttpServletRequest request) {
        operators.requireSession(request);
        return ApiResponse.ok(tasks.complete(taskCode, command, account(request)));
    }

    @PostMapping("/{taskCode}/cancellations")
    public ApiResponse<DeskTask> cancel(@PathVariable String taskCode, @RequestBody CancelDeskTaskCommand command,
                                        HttpServletRequest request) {
        operators.requireSession(request);
        return ApiResponse.ok(tasks.cancel(taskCode, command, account(request)));
    }

    private AuthUserAccount account(HttpServletRequest request) {
        return sessions.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("请先登录后再操作业务"));
    }
}
