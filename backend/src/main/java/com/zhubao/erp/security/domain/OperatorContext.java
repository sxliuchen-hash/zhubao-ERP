package com.zhubao.erp.security.domain;

public record OperatorContext(
        String operator,
        RoleCode role,
        DataScope dataScope
) {
}
