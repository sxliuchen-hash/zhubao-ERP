package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.domain.FinanceRepository;
import com.zhubao.erp.finance.domain.Payable;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.*;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.*;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class InventoryRefactorRegressionTest {
    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 全量重量不符不丢账且入库日期与应付覆盖受保护(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        FactoryItem item = item(f, "bead-item-01", FactoryItemCategory.BEAD, null, null);
        CompanyMaterialReceiptService service = new CompanyMaterialReceiptService(f.items, f.stocks, f.finance, f.unit, guard);
        CompanyMaterialReceipt receipt = service.receive("receipt-buy-01", admin, item.itemId(), n("10"), n("100"), "件", "库", "2026-10-01",
                CompanyMaterialSourceKind.PURCHASE, "供应商", n("50.00"), null);
        CompanyMaterialReceipt read = f.stocks.find(receipt.receiptId()).orElseThrow();
        assertThat(read.businessDate()).isEqualTo("2026-10-01");
        assertThat(read.recordedAt()).isNotBlank();
        assertThat(read.recordedAt()).startsWith(java.time.LocalDate.now().toString());
        CompanyMaterialIssueService issues = new CompanyMaterialIssueService(f.items, f.stocks, f.issues, f.unit, guard);
        assertThatThrownBy(() -> issues.issue("weight-wrong-01", admin, item.itemId(), receipt.batchCode(), "库", "车间", n("10"), n("50"), "件", ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("复称");
        assertThat(f.stocks.balance(item.itemId(), receipt.batchCode(), "库").orElseThrow().weight().value()).isEqualByComparingTo("100");
        assertThat(f.stocks.balance(item.itemId(), receipt.batchCode(), "车间")).isEmpty();
        assertThatThrownBy(() -> service.receive("cover-own-01", admin, item.itemId(), n("10"), null, "件", "库", "2026-10-01",
                CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, receipt.payableCode()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已经对应");
        OperatorContext warehouse = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        assertThatThrownBy(() -> service.receive("cover-noauth-01", warehouse, item.itemId(), n("10"), null, "件", "库", "2026-10-01",
                CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, receipt.payableCode())).isInstanceOf(PermissionDeniedException.class);
        issues.issue("weight-right-01", admin, item.itemId(), receipt.batchCode(), "库", "车间", n("10"), n("100"), "件", "");
        assertThat(f.stocks.balance(item.itemId(), receipt.batchCode(), "库")).isEmpty();
        assertThat(f.stocks.balance(item.itemId(), receipt.batchCode(), "车间").orElseThrow().weight().value()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 同一已有应付并发只允许一次入库(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        FactoryItem item = item(f, "cover-item-01", FactoryItemCategory.BEAD, null, null);
        f.finance.savePayable(new Payable("AP-EXISTING", "公司料", "2026-10", "供应商", "采购", null, "", n("50.00"), n("0.00"), "财务", "2026-10-01T10:00:00"));
        CompanyMaterialReceiptService service = new CompanyMaterialReceiptService(f.items, f.stocks, f.finance, f.unit, guard);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String request = "race-cover-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.receive(request, admin, item.itemId(), n("10"), null, "件", "库", "2026-10-01", CompanyMaterialSourceKind.COVERED_PAYABLE, "", null, "AP-EXISTING");
                        return true;
                    } catch (BusinessException ex) {
                        assertThat(ex).hasMessageContaining("已经对应");
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 实际耗用可分次且未耗用退回不冲减已耗成本(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        FactoryItem item = item(f, "consume-item-01", FactoryItemCategory.CONSUMABLE, ConsumptionTiming.ON_ACTUAL_USE, null);
        f.unit.execute(call("seed-consume-01"), session -> {
            f.stocks.insert(new CompanyMaterialReceipt("GS-CONSUME", item.itemId(), "批", n("10"), "件", StockMeasurement.unknown(), "库",
                    CompanyMaterialSourceKind.OPENING, "", CompanyMaterialAmountState.KNOWN, n("30.00"), "", "", 1), session);
            f.stocks.add(new CompanyMaterialBalance(item.itemId(), "批", "库", n("10"), "件", StockMeasurement.unknown()), session);
            return "seed";
        });
        ConsumableIssueService service = new ConsumableIssueService(f.items, f.stocks, f.issues, f.costs, f.unit, guard);
        var issue = service.issue("consume-issue-01", admin, item.itemId(), "GS-CONSUME", "车间", n("10"));
        var first = service.consume("consume-first-01", admin, issue.issueId(), 1, n("3"));
        var second = service.consume("consume-second-01", admin, issue.issueId(), 2, n("2"));
        assertThat(first.amount()).isEqualByComparingTo("9.00");
        assertThat(second.amount()).isEqualByComparingTo("6.00");
        assertThat(service.consume("consume-second-01", admin, issue.issueId(), 2, n("2")).eventId()).isEqualTo(second.eventId());
        service.returnIssued("consume-return-01", admin, issue.issueId(), 3, n("4"));
        assertThat(service.events(issue.issueId())).hasSize(2);
        assertThatThrownBy(() -> service.consume("consume-too-much", admin, issue.issueId(), 4, n("2"))).isInstanceOf(BusinessException.class);
        assertThat(f.stocks.balance(item.itemId(), "批", "车间").orElseThrow().quantity()).isEqualByComparingTo("1");
        assertThat(f.stocks.balance(item.itemId(), "批", "库").orElseThrow().quantity()).isEqualByComparingTo("4");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 逐件工具校验身份归还可再借报废不可借(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        FactoryItem a = item(f, "piece-item-01", FactoryItemCategory.REUSABLE_TOOL, null, CustodyMode.PIECE);
        FactoryItem b = item(f, "piece-item-02", FactoryItemCategory.REUSABLE_TOOL, null, CustodyMode.PIECE);
        ToolCustodyService service = new ToolCustodyService(f.items, f.tools, f.unit, guard);
        service.placePiece("piece-place-01", admin, a.itemId(), "库", "TOOL-01");
        assertThatThrownBy(() -> service.borrow("piece-wrong-item", admin, b.itemId(), "库", "P1", "甲", null, "TOOL-01")).hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.borrow("piece-wrong-place", admin, a.itemId(), "别库", "P1", "甲", null, "TOOL-01")).hasMessageContaining("不一致");
        var loan = service.borrow("piece-borrow-01", admin, a.itemId(), "库", "P1", "甲", null, "TOOL-01");
        assertThatThrownBy(() -> service.giveBack("piece-half-return", admin, loan.loanId(), 1, n("0.5"))).hasMessageContaining("整件");
        service.giveBack("piece-return-01", admin, loan.loanId(), 1, n("1"));
        assertThat(service.stock(a.itemId(), "库")).isEqualByComparingTo("0");
        var next = service.borrow("piece-borrow-02", admin, a.itemId(), "库", "P2", "乙", null, "TOOL-01");
        service.scrap("piece-scrap-01", admin, next.loanId(), 1, n("1"), "损坏");
        assertThatThrownBy(() -> service.borrow("piece-borrow-03", admin, a.itemId(), "库", "P2", "乙", null, "TOOL-01")).isInstanceOf(BusinessException.class);
        assertThat(service.outstanding(a.itemId(), "P2")).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 盘点只按真实类别读取且拒绝历史与期间库存变动(boolean jdbc) throws Exception {
        Fixture f = fixture(jdbc);
        FactoryItem item = item(f, "stocktake-item", FactoryItemCategory.BEAD, null, null);
        f.unit.execute(call("stocktake-seed"), session -> {
            f.stocks.add(new CompanyMaterialBalance(item.itemId(), "批", "库", n("10"), "件", StockMeasurement.unknown()), session);
            return "seed";
        });
        MaterialStocktakeService service = new MaterialStocktakeService(f.items, f.stocks, f.tools, f.stocktakes, f.unit, guard);
        assertThatThrownBy(() -> service.open("stocktake-wrong", admin, item.itemId(), "批", "库", "NOW", true)).hasMessageContaining("类别不一致");
        assertThatThrownBy(() -> service.open("stocktake-history", admin, item.itemId(), "批", "库", "2026-01-01T00:00:00", false)).hasMessageContaining("历史时点");
        var opened = service.open("stocktake-open", admin, item.itemId(), "批", "库", "NOW", false);
        var counted = service.count("stocktake-count", admin, opened.stocktakeId(), 1, n("10"));
        service.confirm("stocktake-confirm", admin, counted.stocktakeId(), 2);
        assertThat(f.stocks.balance(item.itemId(), "批", "库").orElseThrow().quantity()).isEqualByComparingTo("10");
        var changed = service.open("stocktake-next-open", admin, item.itemId(), "批", "库", "NOW", false);
        service.count("stocktake-next-count", admin, changed.stocktakeId(), 1, n("10"));
        f.unit.execute(call("stocktake-movement"), session -> {
            f.stocks.take(item.itemId(), "批", "库", n("2"), "件", StockMeasurement.unknown(), session);
            return "taken";
        });
        assertThatThrownBy(() -> service.confirm("stocktake-next-confirm", admin, changed.stocktakeId(), 2)).hasMessageContaining("库存已变动");
        assertThat(f.stocks.balance(item.itemId(), "批", "库").orElseThrow().quantity()).isEqualByComparingTo("8");
    }

    private FactoryItem item(Fixture f, String request, FactoryItemCategory category, ConsumptionTiming timing, CustodyMode custody) {
        return new FactoryItemService(f.items, f.unit, guard).register(request, admin, category, request, "规格", "件", timing, custody);
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static CommandCall call(String request) { return new CommandCall(request, "管理员", "SEED", "TEST", request, request); }

    private Fixture fixture(boolean jdbc) throws Exception {
        if (!jdbc) return new Fixture(new InMemoryFactoryItemRepository(), new InMemoryCompanyMaterialReceiptRepository(),
                new InMemoryCompanyMaterialIssueRepository(), new InMemoryConsumableCostRepository(), new InMemoryToolCustodyRepository(),
                new InMemoryMaterialStocktakeRepository(), new InMemoryFinanceRepository(), new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()));
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String file : new String[]{"V36__m3_finance_operations.sql", "V44__m3_command_request.sql", "V97__m3_factory_item.sql",
                    "V98__m3_company_material_receipt.sql", "V99__m3_company_material_issue.sql", "V100__m3_company_material_consumption.sql",
                    "V102__m3_consumable_cost.sql", "V103__m3_tool_custody.sql", "V104__m3_tool_disposition.sql", "V105__m3_material_stocktake.sql",
                    "V137__m3_company_receipt_dates.sql", "V138__m3_tool_piece_disposition.sql"}) {
                for (String sql : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql", file)))) {
                    String lower = sql.stripLeading().toLowerCase(java.util.Locale.ROOT);
                    if (lower.startsWith("create") || lower.startsWith("alter") || lower.startsWith("update inv_tool_piece")) s.execute(sql);
                }
            }
        }
        return new Fixture(new JdbcFactoryItemRepository(ds), new JdbcCompanyMaterialReceiptRepository(ds), new JdbcCompanyMaterialIssueRepository(ds),
                new JdbcConsumableCostRepository(ds), new JdbcToolCustodyRepository(ds), new JdbcMaterialStocktakeRepository(ds),
                new JdbcFinanceRepository(ds), new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds)));
    }

    private record Fixture(FactoryItemRepository items, CompanyMaterialReceiptRepository stocks, CompanyMaterialIssueRepository issues,
                           ConsumableCostRepository costs, ToolCustodyRepository tools, MaterialStocktakeRepository stocktakes,
                           FinanceRepository finance, BusinessWorkUnit unit) {}
}
