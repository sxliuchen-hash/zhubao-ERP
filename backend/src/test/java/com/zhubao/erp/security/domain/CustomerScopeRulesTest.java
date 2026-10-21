package com.zhubao.erp.security.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerScopeRulesTest {

    @Test
    void emptyCustomerListOnlyBlocksCustomerRange() {
        assertThat(CustomerScopeRules.allowed(null, 1L, "客户甲")).isFalse();
        assertThat(CustomerScopeRules.allowed(context(DataScope.TEAM, List.of()), 1L, "客户甲")).isTrue();
        assertThat(CustomerScopeRules.allowed(context(DataScope.DEPARTMENT, List.of()), 1L, "客户甲")).isTrue();
        assertThat(CustomerScopeRules.allowed(context(DataScope.CUSTOMER_RANGE, List.of()), 1L, "客户甲")).isFalse();
    }

    @Test
    void explicitCustomerListStillLimitsOtherDataScopes() {
        OperatorContext operator = context(DataScope.TEAM, List.of("客户#2"));
        assertThat(CustomerScopeRules.allowed(operator, 2L, "客户乙")).isTrue();
        assertThat(CustomerScopeRules.allowed(operator, 1L, "客户甲")).isFalse();
    }

    private OperatorContext context(DataScope scope, List<String> customers) {
        return new OperatorContext("操作员", RoleCode.RECEIVER, scope, false, "", customers);
    }
}
