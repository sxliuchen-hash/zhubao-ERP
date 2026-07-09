package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.CustomerAuthorizationService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Management surface for the customer authorization table. All mutating endpoints are guarded by
 * {@code CUSTOMER_AUTHORIZATION_MANAGE} via {@link com.zhubao.erp.security.web.PermissionGuardInterceptor}.
 */
@RestController
@RequestMapping("/api/security/customer-authorizations")
public class CustomerAuthorizationController {

    private final CustomerAuthorizationService service;
    private final OperatorContextResolver operatorContextResolver;

    public CustomerAuthorizationController(
            CustomerAuthorizationService service,
            OperatorContextResolver operatorContextResolver
    ) {
        this.service = service;
        this.operatorContextResolver = operatorContextResolver;
    }

    @GetMapping
    public ApiResponse<List<CustomerAuthorization>> list(@RequestParam(required = false) String username) {
        if (username == null || username.isBlank()) {
            return ApiResponse.ok(service.allAuthorizations());
        }
        return ApiResponse.ok(service.authorizationsFor(username));
    }

    @PostMapping
    public ApiResponse<CustomerAuthorization> grant(
            @RequestBody GrantRequest request,
            HttpServletRequest httpRequest
    ) {
        String grantedBy = operatorContextResolver.resolve(httpRequest).operator();
        return ApiResponse.ok(service.grant(
                request.username(), request.customerCode(), request.customerName(), grantedBy));
    }

    @PostMapping("/revocations")
    public ApiResponse<RevokeResult> revoke(@RequestBody RevokeRequest request) {
        boolean removed = service.revoke(request.username(), request.customerCode());
        return ApiResponse.ok(new RevokeResult(request.username(), request.customerCode(), removed));
    }

    public record GrantRequest(
            @NotBlank String username,
            @NotBlank String customerCode,
            String customerName
    ) {
    }

    public record RevokeRequest(
            @NotBlank String username,
            @NotBlank String customerCode
    ) {
    }

    public record RevokeResult(String username, String customerCode, boolean removed) {
    }
}
