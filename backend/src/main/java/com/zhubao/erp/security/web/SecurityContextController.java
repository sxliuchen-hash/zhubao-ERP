package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.SecurityCurrentContextService;
import com.zhubao.erp.security.domain.SecurityCurrentContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityContextController {

    private final OperatorContextResolver operatorContextResolver;
    private final SecurityCurrentContextService securityCurrentContextService;

    public SecurityContextController(
            OperatorContextResolver operatorContextResolver,
            SecurityCurrentContextService securityCurrentContextService
    ) {
        this.operatorContextResolver = operatorContextResolver;
        this.securityCurrentContextService = securityCurrentContextService;
    }

    @GetMapping("/api/security/current-context")
    public ApiResponse<SecurityCurrentContext> currentContext(HttpServletRequest request) {
        return ApiResponse.ok(securityCurrentContextService.build(operatorContextResolver.resolve(request)));
    }
}
