package com.zhubao.erp.security.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.infra.InMemoryCustomerAuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAuthorizationServiceTest {

    private CustomerAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAuthorizationService(new InMemoryCustomerAuthorizationRepository());
    }

    @Test
    void grantsAndListsAuthorizationsForUser() {
        service.grant("sales01", "CUST-1001", "恒信珠宝", "总经理");
        service.grant("sales01", "CUST-1002", "雅辰饰品", "总经理");

        assertThat(service.authorizationsFor("sales01"))
                .extracting(CustomerAuthorization::customerCode)
                .containsExactly("CUST-1001", "CUST-1002");
    }

    @Test
    void grantIsIdempotentOnUsernameAndCustomer() {
        service.grant("sales01", "CUST-1001", "恒信珠宝", "总经理");
        service.grant("sales01", "CUST-1001", "恒信珠宝改名", "总经理");

        assertThat(service.authorizationsFor("sales01")).hasSize(1);
        assertThat(service.authorizationsFor("sales01").get(0).customerName()).isEqualTo("恒信珠宝改名");
    }

    @Test
    void revokeRemovesGrant() {
        service.grant("sales01", "CUST-1001", "恒信珠宝", "总经理");

        assertThat(service.revoke("sales01", "CUST-1001")).isTrue();
        assertThat(service.revoke("sales01", "CUST-1001")).isFalse();
        assertThat(service.authorizationsFor("sales01")).isEmpty();
    }

    @Test
    void effectiveScopeUsesAuthorizationTableWhenPresent() {
        service.grant("sales01", "CUST-1001", "恒信珠宝", "总经理");
        service.grant("sales01", "CUST-1002", "雅辰饰品", "总经理");

        assertThat(service.effectiveCustomerScope("sales01", List.of("FALLBACK-1")))
                .containsExactly("CUST-1001", "CUST-1002");
    }

    @Test
    void effectiveScopeFallsBackToAccountScopeWhenNoRows() {
        assertThat(service.effectiveCustomerScope("sales02", List.of("ACCOUNT-SCOPE-9")))
                .containsExactly("ACCOUNT-SCOPE-9");
    }

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> service.grant("", "CUST-1", "x", "gm"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("username");
    }
}
