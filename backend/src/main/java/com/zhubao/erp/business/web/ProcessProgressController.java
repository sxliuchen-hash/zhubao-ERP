package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ProcessProgressService;
import com.zhubao.erp.business.domain.ProcessProgressEvent;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.ProcessProgressRow;
import com.zhubao.erp.business.domain.ProcessProgressTarget;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/business/operations/process-progress")
public class ProcessProgressController {

    private final ProcessProgressService progressService;
    private final OperatorContextResolver operatorContextResolver;
    private final AuthSessionService authSessionService;

    public ProcessProgressController(
            ProcessProgressService progressService,
            OperatorContextResolver operatorContextResolver,
            AuthSessionService authSessionService
    ) {
        this.progressService = progressService;
        this.operatorContextResolver = operatorContextResolver;
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public ApiResponse<List<ProcessProgressRow>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String dueBefore,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(progressService.list(account(request), query, process, dueBefore, page, size));
    }

    @GetMapping("/target")
    public ApiResponse<ProcessProgressTarget> open(@RequestParam String code, HttpServletRequest request) {
        return ApiResponse.ok(progressService.open(code, account(request)));
    }

    @GetMapping("/history")
    public ApiResponse<List<ProcessProgressEvent>> history(
            @RequestParam String orderCode,
            @RequestParam String process,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(progressService.history(orderCode, process, account(request)));
    }

    @PostMapping
    public ApiResponse<ProcessProgressHead> report(@RequestBody ProcessProgressRequest body, HttpServletRequest request) {
        if (body == null) {
            throw new BusinessException("进度报工必须带请求编号");
        }
        return ApiResponse.ok(progressService.report(
                body.code(),
                body.percent(),
                body.expectedVersion(),
                body.note(),
                body.reason(),
                body.requestId(),
                account(request)
        ));
    }

    private AuthUserAccount account(HttpServletRequest request) {
        OperatorContext context = operatorContextResolver.requireSession(request);
        AuthUserAccount account = authSessionService.currentAccount(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new BusinessException("请先登录后再报进度"));
        return new AuthUserAccount(
                account.username(), account.passwordHash(), account.operator(), account.role(), account.dataScope(),
                account.enabled(), account.demoAccount(), account.passwordChangedAt(), account.failedLoginCount(),
                account.lockedUntil(), context.customerScope(), account.passwordChangeRequired(),
                account.temporaryPasswordConsumed(), account.allowedProcesses()
        );
    }

    public record ProcessProgressRequest(
            String code,
            int percent,
            int expectedVersion,
            String note,
            String reason,
            String requestId
    ) {
    }
}
