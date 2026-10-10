package com.zhubao.erp.finance.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinanceReadAuthorizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectsAnonymousOperatorAndOutOfScopeFinanceReads() {
        ResponseEntity<String> anonymous = restTemplate.getForEntity("/api/finance/statements?customerId=91004", String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(403);
        assertThat(anonymous.getBody()).contains("请先登录");

        ResponseEntity<String> operator = exchange(
                "/api/finance/statements?customerId=91004",
                HttpMethod.GET,
                null,
                role("OPERATOR", "SELF", "")
        );
        assertThat(operator.getStatusCode().value()).isEqualTo(403);
        assertThat(operator.getBody()).contains("无权执行");

        HttpHeaders financeAll = role("FINANCE_STATISTICIAN", "ALL", "");
        ResponseEntity<String> saved = exchange("/api/finance/price-agreements", HttpMethod.POST, agreement(91004L, "恒信珠宝"), financeAll);
        assertThat(saved.getStatusCode().value() + " " + saved.getBody()).startsWith("200");
        ResponseEntity<String> other = exchange("/api/finance/price-agreements", HttpMethod.POST, agreement(91005L, "瑞禾珠宝"), financeAll);
        assertThat(other.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> statement = exchange("/api/finance/statements?customerId=91004", HttpMethod.GET, null, financeAll);
        assertThat(statement.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(statement.getBody()).contains("恒信珠宝");

        ResponseEntity<String> payroll = exchange("/api/finance/payroll-lines", HttpMethod.GET, null, financeAll);
        assertThat(payroll.getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<String> company = exchange("/api/finance/operating-statement?yearMonth=2026-10", HttpMethod.GET, null, financeAll);
        assertThat(company.getStatusCode().is2xxSuccessful()).isTrue();

        HttpHeaders scoped = role("FINANCE_STATISTICIAN", "CUSTOMER_RANGE", "91004");
        ResponseEntity<String> allowed = exchange("/api/finance/statements?customerId=91004", HttpMethod.GET, null, scoped);
        assertThat(allowed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allowed.getBody()).contains("恒信珠宝");

        ResponseEntity<String> deniedCustomer = exchange("/api/finance/statements?customerId=91005", HttpMethod.GET, null, scoped);
        assertThat(deniedCustomer.getStatusCode().value()).isEqualTo(403);
        assertThat(deniedCustomer.getBody()).contains("无权查看该客户的财务");

        ResponseEntity<String> emptyCompany = exchange("/api/finance/operating-statement?yearMonth=2026-10", HttpMethod.GET, null, scoped);
        assertThat(emptyCompany.getStatusCode().value()).isEqualTo(403);
        assertThat(emptyCompany.getBody()).contains("只对全公司数据范围开放");

        ResponseEntity<String> scopedOperating = exchange(
                "/api/finance/operating-statement?yearMonth=2026-10&customerId=91004",
                HttpMethod.GET,
                null,
                scoped
        );
        assertThat(scopedOperating.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> payables = exchange("/api/finance/payables", HttpMethod.GET, null, scoped);
        assertThat(payables.getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> wages = exchange("/api/finance/payroll-lines", HttpMethod.GET, null, scoped);
        assertThat(wages.getStatusCode().value()).isEqualTo(403);

        HttpHeaders emptyScope = role("FINANCE_STATISTICIAN", "CUSTOMER_RANGE", "");
        ResponseEntity<String> noCustomers = exchange("/api/finance/statements?customerId=91004", HttpMethod.GET, null, emptyScope);
        assertThat(noCustomers.getStatusCode().value()).isEqualTo(403);
        assertThat(noCustomers.getBody()).contains("没有授权客户");

        ResponseEntity<String> listed = exchange("/api/finance/price-agreements", HttpMethod.GET, null, scoped);
        assertThat(listed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listed.getBody()).contains("恒信珠宝");
        assertThat(listed.getBody()).doesNotContain("瑞禾珠宝");
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, HttpHeaders headers) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders role(String role, String scope, String customers) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Zhubao-Role", role);
        headers.set("X-Zhubao-Operator", "财务读取测试");
        headers.set("X-Zhubao-Data-Scope", scope);
        if (!customers.isBlank()) {
            headers.set("X-Zhubao-Customer-Scope", customers);
        }
        return headers;
    }

    private Map<String, Object> agreement(long customerId, String customerName) {
        return Map.of(
                "customerId", customerId,
                "customerName", customerName,
                "mode", "WEIGHT",
                "effectiveFrom", "2026-10",
                "lines", List.of(Map.of("processName", "", "unit", "GRAM", "unitPrice", "10"))
        );
    }
}
