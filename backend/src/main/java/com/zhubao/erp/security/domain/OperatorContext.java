package com.zhubao.erp.security.domain;

import java.util.List;

public record OperatorContext(
        String operator,
        RoleCode role,
        DataScope dataScope,
        boolean headerBased,
        String sessionToken,
        List<String> customerScope
) {
    public OperatorContext {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
    }

    public OperatorContext(String operator, RoleCode role, DataScope dataScope) {
        this(operator, role, dataScope, true, "", List.of());
    }

    public OperatorContext(String operator, RoleCode role, DataScope dataScope, boolean headerBased, String sessionToken) {
        this(operator, role, dataScope, headerBased, sessionToken, List.of());
    }
}
