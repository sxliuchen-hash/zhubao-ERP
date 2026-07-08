package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.AuthUserAccountManagementService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccountStatusCommand;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.AuthUserAccountUnlockCommand;
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
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
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthSessionService authSessionService;
    private final AuthUserAccountManagementService accountManagementService;
    private final OperatorContextResolver operatorContextResolver;
    private final PermissionGuard permissionGuard;

    public AuthController(
            AuthSessionService authSessionService,
            AuthUserAccountManagementService accountManagementService,
            OperatorContextResolver operatorContextResolver,
            PermissionGuard permissionGuard
    ) {
        this.authSessionService = authSessionService;
        this.accountManagementService = accountManagementService;
        this.operatorContextResolver = operatorContextResolver;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/login")
    public ApiResponse<AuthSession> login(@Valid @RequestBody AuthLoginCommand command) {
        return ApiResponse.ok(authSessionService.login(command));
    }

    @GetMapping("/session")
    public ApiResponse<AuthSession> session(HttpServletRequest request) {
        return ApiResponse.ok(authSessionService
                .session(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER))
                .orElseThrow(() -> new PermissionDeniedException("未登录")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authSessionService.logout(request.getHeader(AuthSessionService.AUTHORIZATION_HEADER));
        return ApiResponse.ok(null);
    }

    @PostMapping("/password")
    public ApiResponse<AuthSession> changeOwnPassword(
            @Valid @RequestBody ChangeOwnPasswordCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(authSessionService.changeOwnPassword(
                request.getHeader(AuthSessionService.AUTHORIZATION_HEADER),
                command
        ));
    }

    @GetMapping("/demo-users")
    public ApiResponse<List<String>> demoUsers() {
        return ApiResponse.ok(authSessionService.demoUsernames());
    }

    @GetMapping("/audit-events")
    public ApiResponse<List<AuthAuditEvent>> auditEvents() {
        return ApiResponse.ok(authSessionService.auditEvents(20));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AuthUserAccountSummary>> accounts(HttpServletRequest request) {
        manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.accounts());
    }

    @PostMapping("/accounts")
    public ApiResponse<AuthUserAccountSummary> upsertAccount(
            @Valid @RequestBody ManageAuthUserAccountCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.upsert(command, manageAccountOperator(request)));
    }

    @PostMapping("/accounts/{username}/status")
    public ApiResponse<AuthUserAccountSummary> updateAccountStatus(
            @PathVariable String username,
            @Valid @RequestBody AuthUserAccountStatusCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.updateEnabled(
                username,
                command.enabled(),
                command.reason(),
                manageAccountOperator(request)
        ));
    }

    @PostMapping("/accounts/{username}/unlock")
    public ApiResponse<AuthUserAccountSummary> unlockAccount(
            @PathVariable String username,
            @Valid @RequestBody AuthUserAccountUnlockCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.unlock(
                username,
                command.reason(),
                manageAccountOperator(request)
        ));
    }

    private OperatorContext manageAccountOperator(HttpServletRequest request) {
        OperatorContext operatorContext = operatorContextResolver.resolve(request);
        if (operatorContext.headerBased() && request.getHeader(OperatorContextResolver.ROLE_HEADER) == null) {
            throw new PermissionDeniedException("账号管理需要登录会话或显式评审角色");
        }
        permissionGuard.assertAllowed(PermissionCode.AUTH_ACCOUNT_MANAGE, operatorContext);
        return operatorContext;
    }
}
