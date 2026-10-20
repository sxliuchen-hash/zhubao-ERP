package com.zhubao.erp.security.domain;

import java.util.List;

public record OperatorContext(
        String operator,
        RoleCode role,
        DataScope dataScope,
        boolean headerBased,
        String sessionToken,
        List<String> customerScope,
        List<String> coverDuties
) {
    public OperatorContext {
        customerScope = customerScope == null ? List.of() : List.copyOf(customerScope);
        coverDuties = coverDuties == null ? List.of() : List.copyOf(coverDuties);
    }

    public OperatorContext(String operator, RoleCode role, DataScope dataScope) {
        this(operator, role, dataScope, true, "", List.of(), List.of());
    }

    public OperatorContext(String operator, RoleCode role, DataScope dataScope, boolean headerBased, String sessionToken) {
        this(operator, role, dataScope, headerBased, sessionToken, List.of(), List.of());
    }

    public OperatorContext(String operator, RoleCode role, DataScope dataScope, boolean headerBased, String sessionToken,
                           List<String> customerScope) {
        this(operator, role, dataScope, headerBased, sessionToken, customerScope, List.of());
    }
}
