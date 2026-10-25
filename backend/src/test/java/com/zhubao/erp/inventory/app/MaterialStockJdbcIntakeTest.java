package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.inventory.infra.*;
import com.zhubao.erp.inventory.support.IntakeJdbcFixture;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 真实 JDBC 的寄存待核、原批次领用、回滚、回放和并发；不等同于目标 MySQL 现场验收。 */
class MaterialStockJdbcIntakeTest {
    JdbcDataSource ds;
    JdbcBusinessDocumentRepository orders;
    JdbcStockDocumentRepository docs;
    StockLedgerService ledger;
    MaterialStockService service;
    final AuthUserAccount keeper = account(DataScope.ALL, List.of());
    @BeforeEach void setup() throws Exception {
        ds = IntakeJdbcFixture.create(); orders = new JdbcBusinessDocumentRepository(ds); docs = new JdbcStockDocumentRepository(ds);
        ledger = new StockLedgerService(new JdbcStockLedgerRepository(ds));
        var unit = new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds));
        var operations = new BusinessOperationService(ledger, null, orders, unit);
        var catalog = new MasterDataService(new JdbcMasterDataRepository(ds));
        catalog.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("COLD", "VACUUM"), "密封"));
        service = new MaterialStockService(ledger, docs, orders, unit, operations, new MobileIntakeService(operations, orders, unit), new InMemoryMotherRequirementSourceRepository(), new PermissionGuard(), null, null, null, catalog);
    }
    StockDesk.Query query() { return new StockDesk.Query("prep", null, "", "", "", "", "", "", "", ""); }
    StockDesk.ReceiptInput receipt(boolean post, BigDecimal weight, String request) { return new StockDesk.ReceiptInput(1001L, "甲", "WH", "客户送达", "2026-10-25", "库管", "", post, List.of(new StockDesk.LineInput("XY", "件", BigDecimal.TEN, weight, "原来料")), request); }
    StockDesk.OpenOrderInput open(String batch, String amount, String request) { var n = new BigDecimal(amount); return new StockDesk.OpenOrderInput(1001L, "甲", "WH", batch, "XY", "件", n, n, "珠子", "抛光", "2026-11-01", "", 0, request); }
    @Test void 待核确认后领用保持总量且回放不重复收货() {
        String code = service.receivePrep(receipt(false, null, "prep-pending-jdbc"), keeper);
        assertThat(ledger.ledgers()).isEmpty(); assertThat(orders.listMotherOrders()).isEmpty();
        assertThat(service.cards(query(), keeper)).singleElement().satisfies(card -> assertThat(card.pending()).isTrue());
        service.confirmReceipt(new StockDesk.ConfirmInput(code, List.of(new StockDesk.LineInput("XY", "件", BigDecimal.TEN, BigDecimal.TEN, "原来料")), "prep-confirm-jdbc"), keeper);
        var batch = service.cards(query(), keeper).get(0);
        var command = open(batch.batchCode(), "4", "prep-open-jdbc");
        String mother = service.openOrder(command, keeper);
        assertThat(service.openOrder(command, keeper)).isEqualTo(mother);
        assertThat(service.cards(query(), keeper).get(0).availableQuantity()).isEqualTo("6");
        assertThat(new JdbcBusinessDocumentRepository(ds).listReceiptLines(mother).get(0).storageRequirements().conditions()).containsExactly("COLD", "VACUUM");
        assertThat(ledger.balances().stream().map(b -> b.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
        assertThat(ledger.ledgers()).noneMatch(e -> e.documentType().equals("inbound_confirm"));
        assertThat(ledger.ledgers()).filteredOn(e -> e.documentType().equals("prep_issue")).hasSize(1);
        assertThatThrownBy(() -> service.openOrder(open(batch.batchCode(), "1", "prep-other-customer"), account(DataScope.CUSTOMER_RANGE, List.of("1002")))).isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
    }
    @Test void 领用单保存失败回滚母单来源库存和请求随后可原请求重试() throws Exception {
        service.receivePrep(receipt(true, BigDecimal.TEN, "rollback-receipt"), keeper); String batch = service.cards(query(), keeper).get(0).batchCode();
        try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("alter table inv_stock_document add constraint reject_issue check (document_kind <> 'PREP_ISSUE')"); }
        var command = open(batch, "4", "rollback-open");
        assertThatThrownBy(() -> service.openOrder(command, keeper)).isInstanceOf(RuntimeException.class);
        assertThat(orders.listMotherOrders()).isEmpty(); assertThat(service.cards(query(), keeper).get(0).availableQuantity()).isEqualTo("10");
        assertThat(ledger.ledgers()).hasSize(1);
        try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("alter table inv_stock_document drop constraint reject_issue"); }
        String mother = service.openOrder(command, keeper); assertThat(service.openOrder(command, keeper)).isEqualTo(mother);
        assertThat(orders.listMotherOrders()).hasSize(1); assertThat(service.cards(query(), keeper).get(0).availableQuantity()).isEqualTo("6");
    }
    @Test void 两个并发领用只允许一笔成功且持久化余额不透支() throws Exception {
        service.receivePrep(receipt(true, BigDecimal.TEN, "race-receipt"), keeper); String batch = service.cards(query(), keeper).get(0).batchCode();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return service.openOrder(open(batch, "8", "race-open-a"), keeper); });
            var b = pool.submit(() -> { start.await(); return service.openOrder(open(batch, "8", "race-open-b"), keeper); }); start.countDown();
            int success = 0; int rejected = 0;
            for (var f : List.of(a, b)) try { f.get(15, TimeUnit.SECONDS); success++; } catch (ExecutionException ex) { assertThat(ex.getCause()).isInstanceOf(com.zhubao.erp.common.error.BusinessException.class); rejected++; }
            assertThat(success).isEqualTo(1); assertThat(rejected).isEqualTo(1); assertThat(orders.listMotherOrders()).hasSize(1);
            assertThat(service.cards(query(), keeper).get(0).availableQuantity()).isEqualTo("2");
        } finally { pool.shutdownNow(); }
    }
    private static AuthUserAccount account(DataScope scope, List<String> customers) { return new AuthUserAccount("keeper", "hash", "库管", RoleCode.WAREHOUSE_KEEPER, scope, true, true, Instant.now(), 0, null, customers, false, false, List.of()); }
}
