package com.zhubao.erp.security.app;

import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OperatorContextResolver {

    public static final String OPERATOR_HEADER = "X-Zhubao-Operator";
    public static final String ROLE_HEADER = "X-Zhubao-Role";
    public static final String DATA_SCOPE_HEADER = "X-Zhubao-Data-Scope";

    public OperatorContext resolve(HttpServletRequest request) {
        String operator = headerOrDefault(request, OPERATOR_HEADER, "system-review");
        RoleCode role = parseRole(headerOrDefault(request, ROLE_HEADER, RoleCode.ADMIN.name()));
        DataScope dataScope = parseDataScope(headerOrDefault(request, DATA_SCOPE_HEADER, DataScope.ALL.name()));
        return new OperatorContext(operator, role, dataScope);
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
}
