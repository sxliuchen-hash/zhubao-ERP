package com.zhubao.erp.business.app;

import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentVisibilityTest {

    @Test
    void unknownOrganizationAndEmptyCustomerScopeDoNotExpandVisibility() {
        OperatorContext stranger = context("陌生人", "stranger", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, "", "");
        OperatorContext keeper = context("库管", "keeper", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, "", "仓储部");
        OperatorContext receiver = context("收货员", "receiver", RoleCode.RECEIVER, DataScope.DEPARTMENT, "", "仓储部");
        OperatorContext otherDepartment = context("库管", "keeper", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, "", "质检部");
        OperatorContext operator = context("操作工", "worker", RoleCode.OPERATOR, DataScope.TEAM, "甲班", "");
        List<String> participants = List.of("收货员");
        List<AuthUserAccount> directory = List.of(
                account("receiver", "收货员", RoleCode.RECEIVER, "", "仓储部"),
                account("keeper", "库管", RoleCode.WAREHOUSE_KEEPER, "", "仓储部")
        );

        assertThat(DocumentVisibility.canSee(stranger, 101L, "客户", "inbound", participants, directory)).isFalse();
        assertThat(DocumentVisibility.canSee(keeper, 101L, "客户", "inbound", participants, List.of())).isFalse();
        assertThat(DocumentVisibility.canSee(keeper, 101L, "客户", "inbound", participants, directory)).isTrue();
        assertThat(DocumentVisibility.canSee(otherDepartment, 101L, "客户", "inbound", participants, directory)).isFalse();
        assertThat(DocumentVisibility.canSee(operator, 101L, "客户", "production", participants, directory)).isFalse();
        assertThat(DocumentVisibility.canSee(context("收货员", "receiver", RoleCode.RECEIVER, DataScope.SELF, "", ""),
                101L, "客户", "inbound", participants, List.of())).isTrue();
        assertThat(DocumentVisibility.canSee(new OperatorContext("业务员", RoleCode.SALES, DataScope.CUSTOMER_RANGE),
                101L, "客户", "inbound", participants, directory)).isFalse();
    }

    private OperatorContext context(String operator, String username, RoleCode role, DataScope scope, String team, String department) {
        return new OperatorContext(operator, role, scope, false, "", List.of(), List.of(), username, team, department);
    }

    private AuthUserAccount account(String username, String operator, RoleCode role, String team, String department) {
        return new AuthUserAccount(username, "hash", operator, role, DataScope.DEPARTMENT, true, false)
                .withOrganization(team, department);
    }
}
