package com.zhubao.erp.security.domain;

public record OperatorContext(
        String operator,
        RoleCode role,
        DataScope dataScope,
        boolean headerBased,
        String sessionToken
) {
    public OperatorContext(String operator, RoleCode role, DataScope dataScope) {
        this(operator, role, dataScope, true, "");
    }
}
