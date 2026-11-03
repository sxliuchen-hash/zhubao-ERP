package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.CustomerPortalBinding;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.SessionRevocationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户入口身份管理。管理员绑定客户并设置或重置口令。账单和照片不在这里开放。
 */
@RestController
@RequestMapping("/api/security/customer-portal")
public class CustomerPortalIdentityController {

    private final CustomerPortalIdentityService service;
    private final OperatorContextResolver operatorContextResolver;

    public CustomerPortalIdentityController(CustomerPortalIdentityService service,
            OperatorContextResolver operatorContextResolver) {
        this.service = service;
        this.operatorContextResolver = operatorContextResolver;
    }

    @PostMapping("/bindings")
    public ApiResponse<CustomerPortalBinding> bind(@RequestBody BindRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.bind(operator(httpRequest), request.username(), request.customerId()));
    }

    @PostMapping("/presented-claims")
    public ApiResponse<CustomerPortalBinding> refusePresentedClaim(@RequestBody PresentedClaimRequest request,
            HttpServletRequest httpRequest) {
        service.refusePresentedClaim(operator(httpRequest), request.username(), request.presented());
        throw new IllegalStateException("客户自填编号不能完成绑定");
    }

    @PostMapping("/credentials")
    public ApiResponse<Void> issueCredential(@RequestBody CredentialRequest request, HttpServletRequest httpRequest) {
        service.issueCredential(operator(httpRequest), request.username(), request.password());
        return ApiResponse.ok(null);
    }

    @PostMapping("/credential-recoveries")
    public ApiResponse<Void> recoverCredential(@RequestBody CredentialRequest request, HttpServletRequest httpRequest) {
        service.recoverCredential(operator(httpRequest), request.username(), request.password());
        return ApiResponse.ok(null);
    }

    @PostMapping("/bindings/{username}/disable")
    public ApiResponse<SessionRevocationResult> disable(@PathVariable String username, HttpServletRequest httpRequest) {
        int revoked = service.disable(operator(httpRequest), username);
        return ApiResponse.ok(new SessionRevocationResult(username, revoked));
    }

    private OperatorContext operator(HttpServletRequest request) {
        return operatorContextResolver.resolve(request);
    }

    public record BindRequest(@NotBlank String username, @NotNull Long customerId) {
    }

    public record PresentedClaimRequest(@NotBlank String username, @NotBlank String presented) {
    }

    public record UsernameRequest(@NotBlank String username) {
    }

    public record CredentialRequest(@NotBlank String username, @NotBlank String password) {
    }
}
