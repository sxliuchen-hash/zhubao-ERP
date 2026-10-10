package com.zhubao.erp.finance.web;

import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.PriceLine;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.finance.domain.ReceivableLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 财务写入口与读取入口使用同样的数据范围，拒绝请求不能产生流水或泄露工资。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinanceWriteAuthorizationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FinanceRepository finance;

    @ParameterizedTest(name = "公司财务写入必须为全公司范围：{0}")
    @MethodSource("companyCommands")
    void rejectsAnonymousAndRestrictedCompanyWrites(String path, Map<String, Object> command) {
        List<Object> before = snapshot();
        for (HttpHeaders headers : List.of(new HttpHeaders(), role("CUSTOMER_RANGE", "92601"), role("SELF", ""))) {
            ResponseEntity<String> denied = post(path, command, headers);
            assertThat(denied.getStatusCode().value()).as(path + ": " + denied.getBody()).isEqualTo(403);
            assertThat(denied.getBody()).doesNotContain("grossAmount", "netAmount", "unitPrice");
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @Test
    void companyFinanceCanGeneratePayrollAndMaintainRates() {
        HttpHeaders all = role("ALL", "");
        ResponseEntity<String> saved = post("/worker-rates", Map.of(
                "processName", "雕刻", "unit", "JOB", "unitPrice", "17", "effectiveFrom", "2040-01", "effectiveTo", "2040-01"
        ), all);
        assertThat(saved.getStatusCode().value()).as(saved.getBody()).isEqualTo(200);
        ResponseEntity<String> generated = post("/payroll-runs", Map.of("yearMonth", "2040-01"), all);
        assertThat(generated.getStatusCode().value()).as(generated.getBody()).isEqualTo(200);
        assertThat(generated.getBody()).contains("2040-01", "lines");
    }

    @Test
    void invalidBearerCannotBecomeDefaultCompanyAdministrator() {
        List<Object> before = snapshot();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid-finance-session");
        assertDenied(post("/payroll-runs", Map.of("yearMonth", "2045-01"), headers));
        assertDenied(post("/price-agreements", agreement(92606L, "伪造令牌客户", "2042-01"), headers));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void customerWritesUseRealCustomerIdAndReversalOwnership() {
        long allowed = 92601L;
        long forbidden = 92602L;
        seedAgreement(allowed, "授权财务客户");
        seedAgreement(forbidden, "范围外财务客户");
        finance.saveReceivable(new ReceivableLine("R-AUTH-OTHER", forbidden, "范围外财务客户", "2041-01",
                "INBOUND", "AUTH-OTHER", "MD-AUTH-OTHER", "", "", new BigDecimal("10"), ChargeUnit.PIECE,
                BigDecimal.ONE, new BigDecimal("10"), "", "", "财务", "2041-01-01T08:00:00"));
        finance.saveReceivable(new ReceivableLine("R-AUTH-OWN", allowed, "授权财务客户", "2041-01",
                "INBOUND", "AUTH-OWN", "MD-AUTH-OWN", "", "", new BigDecimal("10"), ChargeUnit.PIECE,
                BigDecimal.ONE, new BigDecimal("10"), "", "", "财务", "2041-01-01T08:00:00"));
        List<Object> before = snapshot();
        HttpHeaders scoped = role("CUSTOMER_RANGE", Long.toString(allowed));
        assertDenied(post("/price-agreements", agreement(forbidden, "授权财务客户", "2042-01"), scoped));
        assertDenied(post("/billing-runs", Map.of("customerId", forbidden, "yearMonth", "2041-01"), scoped));
        assertDenied(post("/billing-runs", Map.of("yearMonth", "2041-01"), scoped));
        assertDenied(post("/receipts", receipt(forbidden, "授权财务客户"), scoped));
        assertDenied(post("/receivable-reversals", Map.of("receivableCode", "R-AUTH-OTHER", "amount", "1", "reason", "测试冲销"), scoped));
        assertThat(snapshot()).isEqualTo(before);

        ResponseEntity<String> agreement = post("/price-agreements", agreement(allowed, "授权财务客户", "2042-01"), scoped);
        assertThat(agreement.getStatusCode().value()).as(agreement.getBody()).isEqualTo(200);
        ResponseEntity<String> generated = post("/billing-runs", Map.of("customerId", allowed, "yearMonth", "2041-01"), scoped);
        assertThat(generated.getStatusCode().value()).as(generated.getBody()).isEqualTo(200);
        ResponseEntity<String> received = post("/receipts", receipt(allowed, "授权财务客户"), scoped);
        assertThat(received.getStatusCode().value()).as(received.getBody()).isEqualTo(200);
        ResponseEntity<String> reversed = post("/receivable-reversals", Map.of(
                "receivableCode", "R-AUTH-OWN", "amount", "1", "reason", "授权客户冲销"
        ), scoped);
        assertThat(reversed.getStatusCode().value()).as(reversed.getBody()).isEqualTo(200);
        assertThat(reversed.getBody()).contains("R-AUTH-OWN", "授权财务客户");
    }

    @Test
    void requestCustomerNameCannotForgeNameBasedScope() {
        seedAgreement(92603L, "NAME-AUTH-CUSTOMER");
        seedAgreement(92604L, "另一真实客户");
        // 开发身份头按 HTTP 字符集传输，名称使用 ASCII；正式中文名称由登录会话取得。
        HttpHeaders scopedByName = role("CUSTOMER_RANGE", "NAME-AUTH-CUSTOMER");
        List<Object> before = snapshot();
        assertDenied(post("/price-agreements", agreement(92604L, "NAME-AUTH-CUSTOMER", "2042-02"), scopedByName));
        assertDenied(post("/receipts", receipt(92604L, "NAME-AUTH-CUSTOMER"), scopedByName));
        assertDenied(post("/price-agreements", agreement(92605L, "NAME-AUTH-CUSTOMER", "2042-02"), scopedByName));
        assertThat(snapshot()).isEqualTo(before);
        ResponseEntity<String> allowed = post("/price-agreements", agreement(92603L, "NAME-AUTH-CUSTOMER", "2042-02"), scopedByName);
        assertThat(allowed.getStatusCode().value()).as(allowed.getBody()).isEqualTo(200);
    }

    private static Stream<Object[]> companyCommands() {
        return Stream.of(
                new Object[]{"/worker-rates", Map.of("processName", "雕刻", "unit", "JOB", "unitPrice", "17", "effectiveFrom", "2045-01")},
                new Object[]{"/payroll-runs", Map.of("yearMonth", "2045-01")},
                new Object[]{"/payroll-runs/confirmations", Map.of("yearMonth", "2045-01")},
                new Object[]{"/consumable-items", Map.of("code", "AUTH-GLUE", "name", "胶", "unitName", "支")},
                new Object[]{"/consumable-purchases", Map.of("itemCode", "AUTH-GLUE", "quantity", "1", "amount", "1", "supplier", "供应商", "yearMonth", "2045-01")},
                new Object[]{"/consumable-issues", Map.of("itemCode", "AUTH-GLUE", "quantity", "1", "yearMonth", "2045-01")},
                new Object[]{"/fixed-assets", Map.of("name", "测试资产", "category", "设备", "originalValue", "100", "residualValue", "0", "startMonth", "2045-01", "usefulMonths", 12)},
                new Object[]{"/depreciation-runs", Map.of("yearMonth", "2045-01")},
                new Object[]{"/outsource-bills", Map.of("supplier", "外协商", "amount", "100", "yearMonth", "2045-01")},
                new Object[]{"/payable-payments", Map.of("payableCode", "AUTH-PAYABLE", "amount", "1", "paidOn", "2045-01-01")}
        );
    }

    private void seedAgreement(long customerId, String customerName) {
        finance.saveAgreement(new PriceAgreement("AGR-AUTH-" + customerId, customerId, customerName, PricingMode.PIECE,
                "2041-01", "2041-12", List.of(new PriceLine("", ChargeUnit.PIECE, BigDecimal.ONE)), "财务", "2041-01-01T08:00:00"));
    }

    private Map<String, Object> agreement(long customerId, String customerName, String month) {
        return Map.of("customerId", customerId, "customerName", customerName, "mode", "PIECE", "effectiveFrom", month,
                "lines", List.of(Map.of("processName", "", "unit", "PIECE", "unitPrice", "10")));
    }

    private Map<String, Object> receipt(long customerId, String customerName) {
        return Map.of("customerId", customerId, "customerName", customerName, "receivedOn", "2041-01-01", "amount", "1");
    }

    private void assertDenied(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(403);
    }

    private ResponseEntity<String> post(String path, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/finance" + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders role(String scope, String customers) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Zhubao-Role", "FINANCE_STATISTICIAN");
        headers.set("X-Zhubao-Operator", "财务写入授权测试");
        headers.set("X-Zhubao-Data-Scope", scope);
        headers.set("X-Zhubao-Customer-Scope", customers);
        return headers;
    }

    private List<Object> snapshot() {
        return List.of(finance.listAgreements(), finance.listReceivables(), finance.listReceipts(), finance.listWorkerRates(),
                finance.listPayrollLines(), finance.findPayrollRun("2045-01"), finance.listConsumableItems(),
                finance.listConsumableMovements(), finance.listAssets(), finance.listDepreciations(), finance.listPayables(),
                finance.listPayablePayments());
    }
}
