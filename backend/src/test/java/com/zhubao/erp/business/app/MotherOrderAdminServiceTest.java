package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.business.infra.InMemoryOrderChangeRepository;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MotherOrderAdminServiceTest {
    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final InMemoryStockLedgerRepository inventory = new InMemoryStockLedgerRepository();
    private final StockLedgerService stocks = new StockLedgerService(inventory);
    private final InMemoryCommandRequestRepository requests = new InMemoryCommandRequestRepository();
    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);

    private MotherOrderAdminService service(MemoryBusinessWorkUnit workUnit) {
        return new MotherOrderAdminService(documents, new InMemoryMasterDataRepository(), stocks,
                mock(FinanceRepository.class), new InMemoryOrderChangeRepository(), workUnit, new PermissionGuard());
    }

    private void receipt(String material, int line, String quantity, String weight) {
        StockDimension dimension = new StockDimension(OwnerType.CUSTOMER, 1001L, 1001L, "MD-ADMIN", null,
                material, "WH", StockStatus.RAW_WAITING);
        StockLedgerEntry ledger = stocks.transfer(new StockTransferCommand("inbound_confirm", "MD-ADMIN", null,
                dimension, new BigDecimal(quantity), new BigDecimal(weight), "收货"));
        var session = new com.zhubao.erp.work.domain.WorkSession() {
            public java.util.Optional<java.sql.Connection> connection() { return java.util.Optional.empty(); }
            public void onRollback(Runnable undo) { }
        };
        documents.saveReceiptLine("MD-ADMIN", new ReceiptLine(line, material, material, "货品", new BigDecimal(quantity),
                "件", new BigDecimal(weight), ledger.code()), session);
    }

    private void mother() {
        documents.saveMotherOrder(new MotherOrderRecord("MD-ADMIN", 1001L, 1001L, "AU", "2026-10-27", "", "", "", "", "",
                "戒指", "CUSTOMER", true, "WH", new BigDecimal("3"), "件", new BigDecimal("2"),
                "RAW_WAITING", "", null, null, null, "原建单人", "", "原备注", "OPEN", "2026-10-27T09:00",
                "原要求", "2026-11-01", "恒信珠宝"));
    }

    private MotherOrderAdminService.BasicInfo command(String request) {
        return new MotherOrderAdminService.BasicInfo(1002L, "新货品", "新备注", "客户录错", request,
                OrderMaintenanceRevision.of(documents.findMotherOrderAny("MD-ADMIN").orElseThrow()));
    }

    @Test
    void 多材质含零重量更换客户保留原流水且重复请求不重复冲正() {
        mother(); receipt("AU", 1, "1", "2"); receipt("AG", 2, "2", "0");
        MotherOrderAdminService service = service(new MemoryBusinessWorkUnit(requests));
        var command = command("admin-basic-1");
        service.correct("MD-ADMIN", command, admin);
        service.correct("MD-ADMIN", command, admin);
        var next = documents.findMotherOrderAny("MD-ADMIN").orElseThrow();
        assertThat(next.customerId()).isEqualTo(1002L);
        assertThat(next.ownerCustomerId()).isEqualTo(1002L);
        assertThat(next.customerName()).isEqualTo("雅辰饰品");
        assertThat(next.operator()).isEqualTo("原建单人");
        assertThat(next.processingRequirement()).isEqualTo("原要求");
        assertThat(stocks.ledgers()).hasSize(6);
        assertThat(stocks.balances().stream().filter(row -> row.dimension().customerId() == 1001L)
                .map(StockBalance::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isZero();
        assertThat(stocks.balances().stream().filter(row -> row.dimension().customerId() == 1002L)
                .map(StockBalance::quantity).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("3");
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-ADMIN")).hasSize(1);
        assertThatThrownBy(() -> service.correct("MD-ADMIN", new MotherOrderAdminService.BasicInfo(1001L, "旧页面", "", "冲突",
                "admin-basic-stale", command.expectedRevision()), admin)).hasMessageContaining("已被修改");
        service.correct("MD-ADMIN", new MotherOrderAdminService.BasicInfo(1001L, "新货品", "新备注", "再次核对",
                "admin-basic-back", OrderMaintenanceRevision.of(next)), admin);
        assertThat(stocks.ledgers()).hasSize(10);
    }

    @Test
    void 审计或请求结果失败时库存母单明细全部回滚并能原请求重试() {
        mother(); receipt("AU", 1, "1", "2"); receipt("AG", 2, "2", "0");
        AtomicBoolean fail = new AtomicBoolean(true);
        var service = service(new MemoryBusinessWorkUnit(requests, () -> { if (fail.getAndSet(false)) throw new IllegalStateException("模拟结果失败"); }));
        var command = command("admin-basic-rollback");
        var lines = documents.listReceiptLines("MD-ADMIN");
        assertThatThrownBy(() -> service.correct("MD-ADMIN", command, admin)).hasMessageContaining("模拟结果失败");
        assertThat(stocks.ledgers()).hasSize(2);
        assertThat(documents.findMotherOrderAny("MD-ADMIN").orElseThrow().customerId()).isEqualTo(1001L);
        assertThat(documents.listReceiptLines("MD-ADMIN")).isEqualTo(lines);
        assertThat(documents.listOrderAdjustments("MOTHER", "MD-ADMIN")).isEmpty();
        service.correct("MD-ADMIN", command, admin);
        assertThat(stocks.ledgers()).hasSize(6);
    }

    @Test
    void 非管理员无权修改且已有分批交工不能换客户() {
        mother(); receipt("AU", 1, "3", "2");
        var service = service(new MemoryBusinessWorkUnit(requests));
        var command = command("admin-basic-denied");
        assertThatThrownBy(() -> service.correct("MD-ADMIN", command,
                new OperatorContext("业务管理", RoleCode.BUSINESS_MANAGER, DataScope.ALL))).isInstanceOf(PermissionDeniedException.class);
        new MemoryBusinessWorkUnit(requests).execute(new com.zhubao.erp.work.domain.CommandCall("batch-before-admin", "师傅", "BATCH", "MOTHER", "MD-ADMIN", ""), session -> {
            documents.saveProcessBatch(new ProcessBatchHandoff("B1", "MD-ADMIN", "雕刻", BigDecimal.ONE, null, "师傅", "2026-10-27T10:00"), session);
            return "B1";
        });
        assertThatThrownBy(() -> service.correct("MD-ADMIN", command, admin)).hasMessageContaining("不能直接更换客户");
        assertThat(stocks.ledgers()).hasSize(1);
    }

    @Test
    void 同一旧版本并发修改只能成功一次() throws Exception {
        mother(); receipt("AU", 1, "3", "2");
        var service = service(new MemoryBusinessWorkUnit(requests));
        var first = command("admin-concurrent-1");
        var second = command("admin-concurrent-2");
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(() -> attempt(service, first), () -> attempt(service, second)));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
            assertThat(stocks.ledgers()).hasSize(3);
        } finally { pool.shutdownNow(); }
    }

    private boolean attempt(MotherOrderAdminService service, MotherOrderAdminService.BasicInfo command) {
        try { service.correct("MD-ADMIN", command, admin); return true; }
        catch (com.zhubao.erp.common.error.BusinessException ex) { return false; }
    }
}
