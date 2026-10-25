package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.*;
import com.zhubao.erp.inventory.support.IntakeJdbcFixture;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.*;
import com.zhubao.erp.work.infra.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ProcessingIntakePendingServiceTest {
    final OperatorContext operator = new OperatorContext("收货员", RoleCode.ADMIN, DataScope.ALL);
    final ProcessingIntakeDraft draft = new ProcessingIntakeDraft(null, "", "CUSTOMER_DELIVERY", "", "", "", List.of(new ProcessingIntakeDraft.Line("XY", "珠子", "1", "件", "")), "", 0, "local-1");
    record Fixture(BusinessDocumentRepository orders, StockLedgerService ledger, ProcessingIntakePendingRepository pending, ProcessingIntakePendingService service, MobileIntakeService intake, org.h2.jdbcx.JdbcDataSource ds) { }
    Fixture fixture(boolean jdbc) throws Exception {
        var ds = jdbc ? IntakeJdbcFixture.create() : null;
        BusinessDocumentRepository orders = jdbc ? new JdbcBusinessDocumentRepository(ds) : new InMemoryBusinessDocumentRepository();
        var ledger = new StockLedgerService(jdbc ? new JdbcStockLedgerRepository(ds) : new InMemoryStockLedgerRepository());
        BusinessWorkUnit unit = jdbc ? new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds)) : new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ProcessingIntakePendingRepository pending = jdbc ? new JdbcProcessingIntakePendingRepository(ds) : new InMemoryProcessingIntakePendingRepository();
        var service = new ProcessingIntakePendingService(pending, unit, new BusinessDataAccess(orders, ledger));
        var operations = new BusinessOperationService(ledger, null, orders, unit);
        var intake = new MobileIntakeService(operations, orders, unit, null, ledger, null, null, pending);
        return new Fixture(orders, ledger, pending, service, intake, ds);
    }
    MobileIntakeCommand command(String pending, int version, BigDecimal weight, String request) {
        return new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-11-01", "珠子", BigDecimal.ONE, weight, "", "XY", "件", request, "CUSTOMER_DELIVERY", List.of(), "", 0, pending, version);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true}) void 不全资料真实保存回放版本账号隔离并正常转正式单(boolean jdbc) throws Exception {
        var f = fixture(jdbc); var p = f.service.save("", 0, "pending-save-1", draft, "receiver", operator);
        assertThat(f.orders.listMotherOrders()).isEmpty(); assertThat(f.ledger.ledgers()).isEmpty();
        assertThat(f.service.save("", 0, "pending-save-1", draft, "receiver", operator)).isEqualTo(p);
        assertThat(f.service.list("other", operator)).isEmpty();
        assertThatThrownBy(() -> f.service.save(p.code(), 1, "pending-steal", draft, "other", operator)).isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
        var updated = f.service.save(p.code(), 1, "pending-update", draft, "receiver", operator);
        assertThat(updated.version()).isEqualTo(2);
        assertThat(f.service.save("", 0, "pending-save-1", draft, "receiver", operator)).isEqualTo(p);
        assertThatThrownBy(() -> f.service.save(p.code(), 1, "pending-stale", draft, "receiver", operator)).hasMessageContaining("已被修改");
        assertThatThrownBy(() -> f.intake.intake(command(p.code(), 2, null, "pending-empty-weight"), operator, "receiver")).isInstanceOf(RuntimeException.class);
        assertThat(f.orders.listMotherOrders()).isEmpty(); assertThat(f.pending.find(p.code(), null).orElseThrow().status()).isEqualTo("PENDING");
        var cmd = command(p.code(), 2, BigDecimal.ONE, "pending-convert"); var result = f.intake.intake(cmd, operator, "receiver");
        assertThat(f.intake.intake(cmd, operator, "receiver")).isEqualTo(result);
        assertThatThrownBy(() -> f.intake.intake(cmd, operator, "other")).isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
        assertThat(f.pending.find(p.code(), null).orElseThrow().motherOrderCode()).isEqualTo(result.motherOrderCode());
        assertThat(f.service.list("receiver", operator)).isEmpty(); assertThat(f.orders.listMotherOrders()).hasSize(1); assertThat(f.ledger.ledgers()).hasSize(1);
        assertThatThrownBy(() -> f.intake.intake(command(p.code(), 3, BigDecimal.ONE, "pending-second-convert"), operator, "receiver")).hasMessageContaining("已转正式母单");
    }
    @Test void 大于通用回放长度的真实待核资料仍可保存和原请求回放() throws Exception {
        var f = fixture(true);
        var lines = java.util.stream.IntStream.range(0, 20).mapToObj(i -> new ProcessingIntakeDraft.Line("XY", "珠子资料".repeat(20) + i, "1", "件", "")).toList();
        var large = new ProcessingIntakeDraft(null, "", "CUSTOMER_DELIVERY", "", "要求".repeat(200), "", lines, "", 0, "large-local");
        var saved = f.service.save("", 0, "pending-large", large, "receiver", operator);
        assertThat(saved.draftJson().length()).isGreaterThan(2000);
        assertThat(f.service.save("", 0, "pending-large", large, "receiver", operator)).isEqualTo(saved);
        assertThat(f.pending.find(saved.code(), null)).contains(saved);
    }
    @Test void 转换末尾失败整体回滚并可原请求重试() throws Exception {
        var f = fixture(true); var p = f.service.save("", 0, "rollback-pending", draft, "receiver", operator);
        try (var c = f.ds.getConnection(); var s = c.createStatement()) { s.execute("alter table biz_processing_intake_pending add constraint reject_consumed check(status <> 'CONSUMED')"); }
        var cmd = command(p.code(), 1, BigDecimal.ONE, "rollback-convert");
        assertThatThrownBy(() -> f.intake.intake(cmd, operator, "receiver")).isInstanceOf(RuntimeException.class);
        assertThat(f.orders.listMotherOrders()).isEmpty(); assertThat(f.ledger.ledgers()).isEmpty(); assertThat(f.pending.find(p.code(), null).orElseThrow().version()).isEqualTo(1);
        try (var c = f.ds.getConnection(); var s = c.createStatement()) { s.execute("alter table biz_processing_intake_pending drop constraint reject_consumed"); }
        f.intake.intake(cmd, operator, "receiver"); assertThat(f.orders.listMotherOrders()).hasSize(1);
    }
    @Test void 同一待核并发转换仅产生一笔库存() throws Exception {
        var f = fixture(true); var p = f.service.save("", 0, "race-pending", draft, "receiver", operator);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return f.intake.intake(command(p.code(), 1, BigDecimal.ONE, "race-convert-a"), operator, "receiver"); });
            var b = pool.submit(() -> { start.await(); return f.intake.intake(command(p.code(), 1, BigDecimal.ONE, "race-convert-b"), operator, "receiver"); }); start.countDown();
            int ok = 0; int rejected = 0;
            for (var future : List.of(a, b)) try { future.get(15, TimeUnit.SECONDS); ok++; } catch (ExecutionException ex) { assertThat(ex.getCause()).isInstanceOf(com.zhubao.erp.common.error.BusinessException.class); rejected++; }
            assertThat(ok).isEqualTo(1); assertThat(rejected).isEqualTo(1); assertThat(f.ledger.ledgers()).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
}
