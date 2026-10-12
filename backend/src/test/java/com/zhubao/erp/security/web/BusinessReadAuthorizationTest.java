package com.zhubao.erp.security.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.BusinessDocumentRepository;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.document.app.DocumentManagementService;
import com.zhubao.erp.document.domain.DocumentManagementRepository;
import com.zhubao.erp.document.domain.DocumentPackage;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthLoginCommand;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.AuthUserAccountRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 直接访问 HTTP 验证真实会话、客户范围、岗位范围及预览授权。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusinessReadAuthorizationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private BusinessDocumentRepository business;
    @Autowired private DocumentManagementRepository documents;
    @Autowired private DocumentManagementService documentService;
    @Autowired private StockLedgerService stocks;
    @Autowired private AuthUserAccountRepository accounts;
    @Autowired private PasswordHasher hasher;
    @Autowired private AuthSessionService sessions;

    @Test
    void anonymousAndForgedHeadersCannotReadDocumentsOrInventory() {
        HttpHeaders forged = new HttpHeaders();
        forged.set("X-Zhubao-Role", "ADMIN");
        forged.set("X-Zhubao-Data-Scope", "ALL");
        for (String path : List.of("/api/documents/summary", "/api/documents/packages", "/api/documents/attachments",
                "/api/documents/storage", "/api/documents/audit-events", "/api/documents/captures?businessCode=MD-R03-A",
                "/api/documents/attachments/ATT-R03-A/preview-url", "/api/inventory/balances", "/api/inventory/ledgers")) {
            assertThat(get(path, new HttpHeaders()).getStatusCode().value()).as(path).isEqualTo(403);
            assertThat(get(path, forged).getStatusCode().value()).as("伪造请求头：" + path).isEqualTo(403);
        }
        StockTransferCommand transfer = new StockTransferCommand("manual", "ANON-TRANSFER", null,
                new StockDimension(OwnerType.CUSTOMER, 980031L, 980031L, "MD-ANON", null, "GOLD", "RAW", StockStatus.RAW_WAITING),
                BigDecimal.ONE, BigDecimal.ONE, "匿名写入不得成为权限旁路");
        int before = stocks.ledgers().size();
        for (HttpHeaders headers : List.of(new HttpHeaders(), forged)) {
            assertThat(http.postForEntity("/api/inventory/ledger/transfers", new HttpEntity<>(transfer, headers), String.class)
                    .getStatusCode().value()).isEqualTo(403);
        }
        assertThat(stocks.ledgers()).hasSize(before);
    }

    @Test
    void filtersCustomerRowsAndAuthorizesEveryPreviewIssuance() throws Exception {
        seed("R03-A", 980031L, "授权客户", "收货员甲");
        seed("R03-B", 980032L, "其他客户", "收货员乙");
        // 未知归属不能因附件 owner 相同而开放给有限范围账号。
        saveAttachment("ATT-R03-UNKNOWN", "DOC-R03-UNKNOWN", "UNKNOWN-R03", "已上传");
        HttpHeaders scoped = login("r03-scoped", "收货员甲", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, List.of("980031"));
        for (String path : List.of("/api/documents/packages", "/api/documents/attachments", "/api/inventory/balances", "/api/inventory/ledgers")) {
            ResponseEntity<String> response = get(path, scoped);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(response.getBody()).contains("MD-R03-A").doesNotContain("MD-R03-B", "UNKNOWN-R03");
        }
        JsonNode summary = body(get("/api/documents/summary", scoped));
        assertThat(summary.path("packages").asInt()).isEqualTo(1);
        assertThat(summary.path("attachments").asInt()).isEqualTo(1);
        assertThat(get("/api/documents/attachments/ATT-R03-A/preview-url", scoped).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/documents/captures?businessCode=MD-R03-A", scoped).getBody()).contains("expiresAt=");
        assertThat(get("/api/documents/attachments/ATT-R03-B/preview-url", scoped).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/documents/captures?businessCode=MD-R03-B", scoped).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/documents/attachments/ATT-R03-UNKNOWN/preview-url", scoped).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/documents/storage", scoped).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/documents/audit-events", scoped).getStatusCode().value()).isEqualTo(403);

        HttpHeaders empty = login("r03-empty", "空授权", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, List.of());
        assertThat(body(get("/api/documents/attachments", empty)).size()).isZero();
        assertThat(body(get("/api/inventory/balances", empty)).size()).isZero();

        HttpHeaders finance = login("r03-finance", "财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL, List.of());
        assertThat(get("/api/documents/storage", finance).getStatusCode().value()).isEqualTo(403);
        HttpHeaders admin = login("r03-admin", "管理员", RoleCode.ADMIN, DataScope.ALL, List.of());
        assertThat(get("/api/documents/storage", admin).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/documents/audit-events", admin).getStatusCode().value()).isEqualTo(200);

        String signedUrl = documentService.previewUrl("ATT-R03-A").url();
        saveAttachment("ATT-R03-A", "DOC-R03-A", "MD-R03-A", "已下线");
        assertThat(get("/api/documents/attachments/ATT-R03-A/preview-url", scoped).getStatusCode().is4xxClientError()).isTrue();
        assertThat(http.getForEntity(signedUrl, String.class).getStatusCode().is4xxClientError()).isTrue();
        assertThat(body(get("/api/documents/captures?businessCode=MD-R03-A", scoped)).size()).isZero();
    }

    @Test
    void selfAndDepartmentInventoryReadsRequireRealJobAssociation() throws Exception {
        seed("R03-SELF", 980033L, "岗位客户", "本人收货");
        HttpHeaders self = login("r03-self", "本人收货", RoleCode.OPERATOR, DataScope.SELF, List.of());
        assertThat(get("/api/inventory/balances", self).getBody()).contains("MD-R03-SELF");
        HttpHeaders stranger = login("r03-stranger", "其他操作工", RoleCode.OPERATOR, DataScope.DEPARTMENT, List.of());
        assertThat(get("/api/inventory/balances", stranger).getBody()).doesNotContain("MD-R03-SELF");
        HttpHeaders keeper = login("r03-keeper", "岗位库管", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, List.of());
        assertThat(get("/api/inventory/balances", keeper).getBody()).contains("MD-R03-SELF");
    }

    private HttpHeaders login(String username, String operator, RoleCode role, DataScope scope, List<String> customers) {
        String password = "R03-Test-Password-2026!";
        accounts.save(new AuthUserAccount(username, hasher.hash(password), operator, role, scope, true, false, customers));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sessions.login(new AuthLoginCommand(username, password)).token());
        return headers;
    }

    private void seed(String suffix, long customerId, String customer, String operator) {
        String code = "MD-" + suffix;
        business.saveMotherOrder(new MotherOrderRecord(code, customerId, customerId, "MAT-R03", "2026-10-10", "", "SF-" + suffix,
                "", operator, operator, "戒托", "CUSTOMER", true, "RAW-R03", BigDecimal.TEN, "件", BigDecimal.TEN,
                "RAW_WAITING", "", "", null, "", operator, "", "", "INBOUND_CONFIRMED", "2026-10-10T10:00:00", "抛光", "2026-10-20", customer));
        documents.savePackage(new DocumentPackage("DOC-" + suffix, code, "收货", code, operator, "证据", 1, 1, "已关联", "保留", "2026-10-10", "green"));
        saveAttachment("ATT-" + suffix, "DOC-" + suffix, code, "已上传");
        stocks.transfer(new StockTransferCommand("inbound_confirm", code, null,
                new StockDimension(OwnerType.CUSTOMER, customerId, customerId, code, null, "MAT-R03", "RAW-R03", StockStatus.RAW_WAITING),
                BigDecimal.TEN, BigDecimal.TEN, "读取权限测试"));
    }

    private void saveAttachment(String code, String documentCode, String businessCode, String status) {
        documents.saveAttachment(new EvidenceAttachment(code, documentCode, "称重", businessCode, "test.jpg", "image/jpeg",
                "local://r03-authorization-test/" + code, "本地", "手机采集", status, "收货员甲", "sha256:" + code, "2026-10-10", "无", "green", null));
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json.readTree(response.getBody()).path("data");
    }
}
