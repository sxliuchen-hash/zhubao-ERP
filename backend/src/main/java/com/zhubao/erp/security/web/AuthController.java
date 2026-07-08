package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthSessionService authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
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

    @GetMapping("/demo-users")
    public ApiResponse<List<String>> demoUsers() {
        return ApiResponse.ok(authSessionService.demoUsernames());
    }

    @GetMapping("/audit-events")
    public ApiResponse<List<AuthAuditEvent>> auditEvents() {
        return ApiResponse.ok(authSessionService.auditEvents(20));
    }
}
