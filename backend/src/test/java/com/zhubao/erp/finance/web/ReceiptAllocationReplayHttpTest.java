package com.zhubao.erp.finance.web;

import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.ReceivableLine;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 带核销明细的收款必须走真实 HTTP。仓储单独回放不能代替服务先查余额的缺陷。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReceiptAllocationReplayHttpTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FinanceRepository finance;

    @Test
    void fullPartialAndConflictingAllocationReplayUseTheHttpReceipt() {
        finance.saveReceivable(line("AR-HTTP-FULL", 94031L, "回放客户", "80.00"));
        finance.saveReceivable(line("AR-HTTP-PART", 94031L, "回放客户", "80.00"));
        finance.saveReceivable(line("AR-HTTP-OTHER", 94032L, "范围外客户", "80.00"));
        HttpHeaders company = role("ALL", "");
        Map<String, Object> full = receipt(94031L, "回放客户", "80", "receipt-http-full-01", "AR-HTTP-FULL", "80");
        ResponseEntity<String> created = post(full, company);
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(200);
        ResponseEntity<String> replay = post(full, company);
        assertThat(replay.getStatusCode().value()).as(replay.getBody()).isEqualTo(200);
        assertThat(replay.getBody()).contains(codeOf(created.getBody()));
        assertThat(finance.listReceipts().stream().filter(item -> item.customerId() == 94031L && item.amount().compareTo(new BigDecimal("80.00")) == 0)).hasSize(1);

        Map<String, Object> partial = receipt(94031L, "回放客户", "30", "receipt-http-part-01", "AR-HTTP-PART", "30");
        ResponseEntity<String> part = post(partial, company);
        assertThat(part.getStatusCode().value()).as(part.getBody()).isEqualTo(200);
        ResponseEntity<String> follow = post(receipt(94031L, "回放客户", "40", "receipt-http-part-02", "AR-HTTP-PART", "40"), company);
        assertThat(follow.getStatusCode().value()).as(follow.getBody()).isEqualTo(200);
        ResponseEntity<String> partReplay = post(partial, company);
        assertThat(partReplay.getStatusCode().value()).as(partReplay.getBody()).isEqualTo(200);
        assertThat(partReplay.getBody()).contains(codeOf(part.getBody()));

        ResponseEntity<String> conflict = post(receipt(94031L, "回放客户", "70", "receipt-http-full-01", "AR-HTTP-FULL", "70"), company);
        assertThat(conflict.getStatusCode().value()).as(conflict.getBody()).isEqualTo(400);
        assertThat(conflict.getBody()).contains("内容不一致");

        HttpHeaders scoped = role("CUSTOMER_RANGE", "94031");
        int before = finance.listReceipts().size();
        ResponseEntity<String> crossed = post(receipt(94032L, "范围外客户", "80", "receipt-http-other-01", "AR-HTTP-OTHER", "80"), scoped);
        assertThat(crossed.getStatusCode().value()).as(crossed.getBody()).isEqualTo(403);
        ResponseEntity<String> forged = post(receipt(94031L, "回放客户", "80", "receipt-http-full-01", "AR-HTTP-FULL", "80"), scoped);
        assertThat(forged.getStatusCode().value()).as(forged.getBody()).isEqualTo(200);
        assertThat(forged.getBody()).contains(codeOf(created.getBody()));
        ResponseEntity<String> stolenKey = post(receipt(94031L, "回放客户", "80", "receipt-http-other-01", "AR-HTTP-OTHER", "80"), scoped);
        assertThat(stolenKey.getStatusCode().value()).isIn(400, 403);
        assertThat(finance.listReceipts()).hasSize(before);
    }

    private static ReceivableLine line(String code, long customerId, String customerName, String amount) {
        return new ReceivableLine(code, customerId, customerName, "2026-10", "FIXTURE", "FIX-" + code, "MD-" + code, code,
                "", BigDecimal.ONE, ChargeUnit.PIECE, BigDecimal.ONE, new BigDecimal(amount), "", "历史夹具", "财务",
                "2026-10-01T00:00:00");
    }

    private static Map<String, Object> receipt(long customerId, String customerName, String amount, String key, String receivableCode, String allocation) {
        return Map.of(
                "customerId", customerId,
                "customerName", customerName,
                "receivedOn", "2026-10-10",
                "amount", amount,
                "idempotencyKey", key,
                "allocations", List.of(Map.of("receivableCode", receivableCode, "amount", allocation))
        );
    }

    private ResponseEntity<String> post(Map<String, Object> body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/finance/receipts", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static HttpHeaders role(String scope, String customers) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Zhubao-Role", "FINANCE_STATISTICIAN");
        headers.set("X-Zhubao-Operator", "收款回放测试");
        headers.set("X-Zhubao-Data-Scope", scope);
        headers.set("X-Zhubao-Customer-Scope", customers);
        return headers;
    }

    private static String codeOf(String body) {
        int marker = body.indexOf("FIN-RC");
        assertThat(marker).isGreaterThanOrEqualTo(0);
        int end = marker;
        while (end < body.length() && (Character.isLetterOrDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        return body.substring(marker, end);
    }
}
