package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class OperatorContextResolver {

    public static final String OPERATOR_HEADER = "X-Zhubao-Operator";
    public static final String ROLE_HEADER = "X-Zhubao-Role";
    public static final String DATA_SCOPE_HEADER = "X-Zhubao-Data-Scope";
    public static final String CUSTOMER_SCOPE_HEADER = "X-Zhubao-Customer-Scope";

    private final AuthSessionService authSessionService;

    public OperatorContextResolver(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    public OperatorContext resolve(HttpServletRequest request) {
        String authorization = request.getHeader(AuthSessionService.AUTHORIZATION_HEADER);
        return authSessionService.operatorContext(authorization).orElseGet(() -> resolveHeaders(request));
    }

    private OperatorContext resolveHeaders(HttpServletRequest request) {
        String operator = headerOrDefault(request, OPERATOR_HEADER, "system-review");
        RoleCode role = parseRole(headerOrDefault(request, ROLE_HEADER, RoleCode.ADMIN.name()));
        DataScope dataScope = parseDataScope(headerOrDefault(request, DATA_SCOPE_HEADER, DataScope.ALL.name()));
        List<String> customerScope = parseListHeader(request.getHeader(CUSTOMER_SCOPE_HEADER));
        return new OperatorContext(operator, role, dataScope, true, "", customerScope);
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private RoleCode parseRole(String rawRole) {
        try {
            return RoleCode.valueOf(normalize(rawRole));
        } catch (IllegalArgumentException ex) {
            throw new PermissionDeniedException("未知角色：" + rawRole);
        }
    }

    private DataScope parseDataScope(String rawScope) {
        try {
            return DataScope.valueOf(normalize(rawScope));
        } catch (IllegalArgumentException ex) {
            throw new PermissionDeniedException("未知数据范围：" + rawScope);
        }
    }

    private String normalize(String value) {
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private List<String> parseListHeader(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，;；|]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }
}
