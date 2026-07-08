package com.zhubao.erp.security.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.SecurityCurrentContextService;
import com.zhubao.erp.security.domain.NavigationMenuItem;
import com.zhubao.erp.security.domain.SecurityCurrentContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SecurityContextController {

    private final OperatorContextResolver operatorContextResolver;
    private final NavigationMenuService navigationMenuService;
    private final SecurityCurrentContextService securityCurrentContextService;

    public SecurityContextController(
            OperatorContextResolver operatorContextResolver,
            NavigationMenuService navigationMenuService,
            SecurityCurrentContextService securityCurrentContextService
    ) {
        this.operatorContextResolver = operatorContextResolver;
        this.navigationMenuService = navigationMenuService;
        this.securityCurrentContextService = securityCurrentContextService;
    }

    @GetMapping("/api/security/current-context")
    public ApiResponse<SecurityCurrentContext> currentContext(HttpServletRequest request) {
        return ApiResponse.ok(securityCurrentContextService.build(operatorContextResolver.resolve(request)));
    }

    @GetMapping("/api/security/menu-tree")
    public ApiResponse<List<NavigationMenuItem>> menuTree(HttpServletRequest request) {
        return ApiResponse.ok(navigationMenuService.menuTreeFor(operatorContextResolver.resolve(request).role()));
    }
}
