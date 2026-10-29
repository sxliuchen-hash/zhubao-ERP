package com.zhubao.erp.label.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.MotherSplitState;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.label.domain.PrintJob;
import com.zhubao.erp.label.domain.PrintJobStatus;
import com.zhubao.erp.label.domain.LabelLayout;
import com.zhubao.erp.label.domain.LabelTemplateField;
import com.zhubao.erp.label.domain.SaveLabelTemplateCommand;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintJobServiceTest {

    private InMemoryPrintJobRepository repository;
    private PrintJobService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPrintJobRepository();
        service = new PrintJobService(repository);
    }

    @Test
    void 打印任务冻结模板版式和业务字段() throws Exception {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(new MotherOrderRecord(
                "MD-LAYOUT", 8L, 8L, "AU9999", "2026-07-09", "", "",
                "", "", "", "黄金原料", "CUSTOMER", true, "WH01",
                BigDecimal.ONE, "件", BigDecimal.ONE, "IN_STOCK", "LG001",
                null, null, null, "operator01", "EV001", null, "ACTIVE", "2026-07-09 10:00",
                "来料加工", "2026-07-20", "恒信珠宝"));
        documents.saveMotherSplitState(new MotherSplitState(
                "MD-LAYOUT", MotherSplitState.KEEP, "K7M2", "operator01", "2026-07-09T10:00:00"));
        InMemoryLabelTemplateRepository templates = new InMemoryLabelTemplateRepository();
        LabelLayout layout = new LabelLayout("COMPACT", 16.0, 10.0, 7.0, null, null, null);
        new LabelTemplateService(templates).save(new SaveLabelTemplateCommand(
                null, "MOTHER-CUSTOM", "定制", "MOTHER", "新抬头",
                List.of(new LabelTemplateField("客户", "customer", 1)), "", "", false, true,
                48, 32, 300, "", 1, "", "operator01", layout));
        service.setDocumentAccess(documents, null);
        service.setLabelService(new LabelService(documents, templates));

        PrintJob job = history(service, "print-layout-0001", "MD-LAYOUT", "MOTHER-CUSTOM", 2, "何琳");
        JsonNode snapshot = new ObjectMapper().readTree(job.contentSnapshot());

        assertThat(snapshot.path("widthMm").asInt()).isEqualTo(48);
        assertThat(snapshot.path("heightMm").asInt()).isEqualTo(32);
        assertThat(snapshot.path("dpi").asInt()).isEqualTo(300);
        assertThat(snapshot.path("brandTitle").asText()).isEqualTo("新抬头");
        assertThat(snapshot.path("fields").size()).isEqualTo(1);
        assertThat(snapshot.path("fields").get(0).path("name").asText()).isEqualTo("客户");
        assertThat(snapshot.path("fields").get(0).path("value").asText()).isEqualTo("恒信珠宝");
        assertThat(snapshot.path("layout").path("mode").asText()).isEqualTo("COMPACT");
        assertThat(snapshot.path("layout").path("qrWidthMm").asDouble()).isEqualTo(16.0);
        assertThat(snapshot.path("qr").asText()).startsWith("JG|mother|MD-LAYOUT|");
        PrintJob defaultJob = history(service, "print-layout-default", "MD-LAYOUT", "FLOW", 1, "何琳");
        JsonNode defaultSnapshot = new ObjectMapper().readTree(defaultJob.contentSnapshot());
        assertThat(defaultSnapshot.path("fields").isArray()).isTrue();
        assertThat(defaultSnapshot.path("qr").asText()).startsWith("JG|mother|MD-LAYOUT|");
    }

    @Test
    void 子单标签固定读取母单关系并冻结到新任务快照() throws Exception {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(
                "ZD-20261015-0001", "MD-20261015-0007", 8L, "恒信珠宝", "抛光", "2026-10-20",
                "AU9999", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW-CUSTOMER",
                "抛光", "K7M2", "LG-1", "何琳", "2026-10-15T10:00:00"));
        service.setDocumentAccess(documents, null);
        service.setLabelService(new LabelService(documents, new InMemoryLabelTemplateRepository()));

        PrintJob job = history(service, "print-child-0001", "ZD-20261015-0001", "FLOW", 1, "何琳");
        JsonNode snapshot = new ObjectMapper().readTree(job.contentSnapshot());
        assertThat(snapshot.path("primaryCode").asText()).isEqualTo("ZD-20261015-0001");
        assertThat(snapshot.path("qr").asText()).startsWith("JG|work_order|ZD-20261015-0001|");
        assertThat(snapshot.path("fields").toString()).contains("\"name\":\"母单\"")
                .contains("MD-20261015-0007");
    }

    @Test
    void getExpiresLeaseWithoutWaitingForTheNextClaim() {
        repository.insertIfAbsent(new PrintJob("PJ-HISTORY", "print-history", "MD-3", "FLOW", 1, "{}", PrintJobStatus.LEASED,
                "STATION-1", "2020-01-01T00:00:00", "", "", "", "测试", "2020-01-01", "2020-01-01"));
        assertThat(service.get("PJ-HISTORY").status()).isEqualTo(PrintJobStatus.UNCERTAIN);
        assertThat(service.list()).singleElement().satisfies(job -> assertThat(job.status()).isEqualTo(PrintJobStatus.UNCERTAIN));
    }

    @Test
    void anotherCustomerCannotReadOrCreateTheJob() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        StockLedgerService ledger = new StockLedgerService(new InMemoryStockLedgerRepository());
        new BusinessOperationService(ledger, null, documents, com.zhubao.erp.business.BusinessWorkFixture.unit()).confirmInbound(new ConfirmInboundCommand("MD-PRINT-AUTH", 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF-PRINT-AUTH", "", "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER", new BigDecimal("1"), "件", new BigDecimal("1"), "何琳", "", "", "内壁刻字", "2026-10-20", "恒信珠宝", "fixture-" + java.util.UUID.randomUUID()));
        PrintJobService guarded = new PrintJobService(repository);
        guarded.setDocumentAccess(documents, new BusinessDataAccess(documents, ledger));
        AuthUserAccount owner = account(DataScope.ALL, List.of());
        AuthUserAccount other = account(DataScope.CUSTOMER_RANGE, List.of("2002"));
        PrintJob job = history(guarded, "print-auth-0001", "MD-PRINT-AUTH", "FLOW", 1, owner);
        assertThatThrownBy(() -> guarded.get(job.id(), other)).isInstanceOf(PermissionDeniedException.class);
        assertThat(guarded.list(other)).isEmpty();
        assertThatThrownBy(() -> history(guarded, "print-auth-0002", "MD-PRINT-AUTH", "FLOW", 1, other))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> history(guarded, "print-auth-0003", "MD-MISSING", "FLOW", 1, owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    private PrintJob history(PrintJobService labels, String request, String code, String template, int copies, String operator) {
        return history(labels, request, code, template, copies, account(DataScope.ALL, List.of()));
    }
    private PrintJob history(PrintJobService labels, String request, String code, String template, int copies, AuthUserAccount account) {
        String snapshot = labels.prepareBatchSnapshot(code, template, copies, account);
        return repository.insertIfAbsent(new PrintJob("PJ-" + request, request, code, template, copies, snapshot, PrintJobStatus.SPOOLED,
                "STATION-TEST", "", "", "", "", account.operator(), "2026-10-28", "2026-10-28"));
    }

    private AuthUserAccount account(DataScope scope, List<String> customers) {
        return new AuthUserAccount(
                "打印员", "hash", "打印员", RoleCode.WAREHOUSE_KEEPER, scope, true, true,
                Instant.now(), 0, null, customers, false, false, List.of()
        );
    }
}
