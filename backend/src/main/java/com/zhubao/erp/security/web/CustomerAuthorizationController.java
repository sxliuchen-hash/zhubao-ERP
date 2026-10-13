package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.CustomerAuthorizationService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
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
 * 客户授权兼容管理接口，读取和写入均仅允许系统管理员。
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
    public ApiResponse<List<CustomerAuthorization>> list(@RequestParam(required = false) String username,
                                                        HttpServletRequest httpRequest) {
        manageOperator(httpRequest);
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
        return ApiResponse.ok(service.grant(
                request.username(), request.customerCode(), request.customerName(), manageOperator(httpRequest)));
    }

    @PostMapping("/revocations")
    public ApiResponse<RevokeResult> revoke(@RequestBody RevokeRequest request, HttpServletRequest httpRequest) {
        boolean removed = service.revoke(request.username(), request.customerCode(), manageOperator(httpRequest));
        return ApiResponse.ok(new RevokeResult(request.username(), request.customerCode(), removed));
    }

    private OperatorContext manageOperator(HttpServletRequest request) {
        OperatorContext operatorContext = operatorContextResolver.resolve(request);
        if (operatorContext.role() != RoleCode.ADMIN
                || operatorContext.headerBased() && request.getHeader(OperatorContextResolver.ROLE_HEADER) == null) {
            throw new PermissionDeniedException("客户授权管理需要系统管理员登录会话或显式评审角色");
        }
        return operatorContext;
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
