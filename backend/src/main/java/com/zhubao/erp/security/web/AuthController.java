package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.AuthUserAccountManagementService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplication;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalApplicationCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraft;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalDraftCommand;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalEvidenceSummary;
import com.zhubao.erp.security.domain.AuthAuditEvent;
import com.zhubao.erp.security.domain.AuthAuditIntegritySummary;
import com.zhubao.erp.security.domain.AuthAccountPreApprovalReadiness;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthSession;
import com.zhubao.erp.security.domain.AuthUserAccountStatusCommand;
import com.zhubao.erp.security.domain.AuthUserAccountSummary;
import com.zhubao.erp.security.domain.AuthUserAccountUnlockCommand;
import com.zhubao.erp.security.domain.ChangeOwnPasswordCommand;
import com.zhubao.erp.security.domain.ManageAuthUserAccountCommand;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
import com.zhubao.erp.security.domain.SessionRevocationResult;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @GetMapping("/audit-integrity")
    public ApiResponse<AuthAuditIntegritySummary> auditIntegrity() {
        return ApiResponse.ok(authSessionService.auditIntegrity(20));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AuthUserAccountSummary>> accounts(HttpServletRequest request) {
        manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.accounts());
    }

    @GetMapping("/accounts/pre-approval-readiness")
    public ApiResponse<List<AuthAccountPreApprovalReadiness>> accountPreApprovalReadiness(HttpServletRequest request) {
        manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.preApprovalReadiness());
    }

    @PostMapping("/accounts/pre-approval-drafts")
    public ApiResponse<AuthAccountPreApprovalDraft> draftAccountPreApproval(
            @Valid @RequestBody AuthAccountPreApprovalDraftCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.preApprovalDraft(command, manageAccountOperator(request)));
    }

    @GetMapping("/accounts/pre-approval-applications")
    public ApiResponse<List<AuthAccountPreApprovalApplication>> accountPreApprovalApplications(
            HttpServletRequest request
    ) {
        manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.preApprovalApplications());
    }

    @GetMapping("/accounts/pre-approval-evidence")
    public ApiResponse<List<AuthAccountPreApprovalEvidenceSummary>> accountPreApprovalEvidence(
            HttpServletRequest request
    ) {
        manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.preApprovalEvidence());
    }

    @PostMapping(value = "/accounts/pre-approval-evidence/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AuthAccountPreApprovalEvidenceSummary> uploadAccountPreApprovalEvidence(
            @RequestParam String applicationCode,
            @RequestParam MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        OperatorContext operatorContext = manageAccountOperator(request);
        return ApiResponse.ok(accountManagementService.uploadPreApprovalEvidence(
                applicationCode,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                operatorContext
        ));
    }

    @PostMapping("/accounts/pre-approval-applications")
    public ApiResponse<AuthAccountPreApprovalApplication> submitAccountPreApprovalApplication(
            @Valid @RequestBody AuthAccountPreApprovalApplicationCommand command,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.submitPreApprovalApplication(command, manageAccountOperator(request)));
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
                command.preApprovalApplicationCode(),
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
                command.preApprovalApplicationCode(),
                manageAccountOperator(request)
        ));
    }

    @PostMapping("/accounts/{username}/session-revocations")
    public ApiResponse<SessionRevocationResult> retrySessionRevocation(
            @PathVariable String username,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(accountManagementService.retrySessionRevocation(username, manageAccountOperator(request)));
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
