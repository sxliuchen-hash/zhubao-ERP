package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.FinanceCostService;
import com.zhubao.erp.finance.domain.FinanceCommands.IssueConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PurchaseConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveConsumableItemCommand;
import com.zhubao.erp.finance.domain.PayablePayment;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.ConsumableCostEvent;
import com.zhubao.erp.inventory.domain.ConsumptionTiming;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryConsumableAdoptionRepository;
import com.zhubao.erp.inventory.infra.InMemoryConsumableCostRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcConsumableAdoptionRepository;
import com.zhubao.erp.inventory.infra.JdbcConsumableCostRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumableIssueReturnTest {

    private final OperatorContext admin = new OperatorContext("库管", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 选定计耗后同一数量不重复计费且付款不再计成本() throws Exception {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryConsumableCostRepository costs = new InMemoryConsumableCostRepository();
        InMemoryConsumableAdoptionRepository adoptions = new InMemoryConsumableAdoptionRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FinanceCostService oldBook = new FinanceCostService(finance, new ConsumableOpeningAdoptionService(
                finance, items, stocks, adoptions, unit, guard));
        oldBook.saveItem(new SaveConsumableItemCommand("GLUE", "胶水", "瓶"));
        oldBook.purchase(new PurchaseConsumableCommand("GLUE", new BigDecimal("10"), new BigDecimal("30.00"), "甲", "2026-10"), "财务");
        oldBook.issue(new IssueConsumableCommand("GLUE", new BigDecimal("4"), null, "2026-10"), "财务");
        var adopted = new ConsumableOpeningAdoptionService(finance, items, stocks, adoptions, unit, guard)
                .adopt("f06-adopt1", admin, "GLUE", "50ml", "WH-01");
        FactoryItemService catalog = new FactoryItemService(items, unit, guard);
        ConsumableIssueService service = new ConsumableIssueService(items, stocks, issues, costs, unit, guard);
        assertThatThrownBy(() -> service.issue(
                "f06-early01", admin, adopted.factoryItemId(), adopted.receiptId(), "WS-01", new BigDecimal("1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("耗材计耗方式尚未选定");

        catalog.recordPolicy("f06-policy1", admin, adopted.factoryItemId(), 1, null, ConsumptionTiming.ON_ISSUE, null);
        CompanyMaterialIssue issued = service.issue(
                "f06-issue01", admin, adopted.factoryItemId(), adopted.receiptId(), "WS-01", new BigDecimal("2"));
        assertThat(service.events(issued.issueId())).hasSize(1);
        assertThat(service.events(issued.issueId()).get(0).amount()).isEqualByComparingTo("6.00");
        finance.registerPayablePayment(new PayablePayment(
                "PAY-F06-01", finance.listPayables().get(0).code(), new BigDecimal("30.00"),
                "2026-10-30", "财务", "2026-10-30T00:00:00"), "f06-pay-01");
        assertThat(service.events(issued.issueId())).hasSize(1);

        service.returnIssued("f06-back-01", admin, issued.issueId(), 1, new BigDecimal("1"));
        assertThat(service.events(issued.issueId())).hasSize(2);
        assertThat(service.events(issued.issueId()).stream()
                .filter(event -> ConsumableCostEvent.ADJUSTMENT.equals(event.kind()))
                .findFirst().orElseThrow().amount()).isEqualByComparingTo("-3.00");
        service.returnIssued("f06-back-01", admin, issued.issueId(), 1, new BigDecimal("1"));
        assertThat(service.events(issued.issueId())).hasSize(2);
        assertThatThrownBy(() -> service.consume("f06-use-001", admin, issued.issueId(), 2, new BigDecimal("1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("领用已经计耗，不能再计一次");

        oldBook.saveItem(new SaveConsumableItemCommand("PAPER", "砂纸", "张"));
        oldBook.purchase(new PurchaseConsumableCommand("PAPER", new BigDecimal("8"), new BigDecimal("16.00"), "乙", "2026-10"), "财务");
        var paper = new ConsumableOpeningAdoptionService(finance, items, stocks, adoptions, unit, guard)
                .adopt("f06-adopt2", admin, "PAPER", "240目", "WH-02");
        catalog.recordPolicy("f06-policy2", admin, paper.factoryItemId(), 1, null, ConsumptionTiming.ON_ACTUAL_USE, null);
        CompanyMaterialIssue moved = service.issue(
                "f06-move-01", admin, paper.factoryItemId(), paper.receiptId(), "WS-02", new BigDecimal("3"));
        assertThat(service.events(moved.issueId())).isEmpty();
        assertThat(stocks.balance(paper.factoryItemId(), stocks.find(paper.receiptId()).orElseThrow().batchCode(), "WS-02")
                .orElseThrow().quantity()).isEqualByComparingTo("3");
        ConsumableCostEvent used = service.consume("f06-real-01", admin, moved.issueId(), 1, new BigDecimal("3"));
        assertThat(used.amount()).isEqualByComparingTo("6.00");
        assertThat(service.events(moved.issueId())).hasSize(1);
        service.consume("f06-real-01", admin, moved.issueId(), 1, new BigDecimal("3"));
        assertThat(service.events(moved.issueId())).hasSize(1);
        assertThat(finance.listReceivables()).isEmpty();
    }

    @Test
    void 并发领用不超过结存() throws Exception {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryConsumableCostRepository costs = new InMemoryConsumableCostRepository();
        InMemoryConsumableAdoptionRepository adoptions = new InMemoryConsumableAdoptionRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FinanceCostService oldBook = new FinanceCostService(finance);
        oldBook.saveItem(new SaveConsumableItemCommand("ONE", "一只", "个"));
        oldBook.purchase(new PurchaseConsumableCommand("ONE", new BigDecimal("1"), new BigDecimal("4.00"), "甲", "2026-10"), "财务");
        var adopted = new ConsumableOpeningAdoptionService(finance, items, stocks, adoptions, unit, guard)
                .adopt("f06-one-ad", admin, "ONE", "单件", "WH-01");
        new FactoryItemService(items, unit, guard).recordPolicy(
                "f06-one-po", admin, adopted.factoryItemId(), 1, null, ConsumptionTiming.ON_ISSUE, null);
        ConsumableIssueService service = new ConsumableIssueService(items, stocks, issues, costs, unit, guard);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        for (int index = 0; index < 2; index++) {
            int number = index;
            pool.submit(() -> {
                try {
                    start.await();
                    service.issue("f06-race-" + number, admin, adopted.factoryItemId(), adopted.receiptId(), "WS-01", BigDecimal.ONE);
                    success.incrementAndGet();
                } catch (BusinessException ex) {
                    denied.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(success.get()).isEqualTo(1);
        assertThat(denied.get()).isEqualTo(1);
        assertThat(stocks.balance(adopted.factoryItemId(), stocks.find(adopted.receiptId()).orElseThrow().batchCode(), "WH-01")).isEmpty();
    }

    @Test
    void jdbc领用回放不二次计费() throws Exception {
        JdbcDataSource dataSource = dataSource();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcCompanyMaterialIssueRepository issues = new JdbcCompanyMaterialIssueRepository(dataSource);
        JdbcConsumableCostRepository costs = new JdbcConsumableCostRepository(dataSource);
        JdbcConsumableAdoptionRepository adoptions = new JdbcConsumableAdoptionRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        FinanceCostService oldBook = new FinanceCostService(finance);
        oldBook.saveItem(new SaveConsumableItemCommand("GLUE", "胶水", "瓶"));
        oldBook.purchase(new PurchaseConsumableCommand("GLUE", new BigDecimal("6"), new BigDecimal("18.00"), "甲", "2026-10"), "财务");
        var adopted = new ConsumableOpeningAdoptionService(finance, items, stocks, adoptions, unit, guard)
                .adopt("f06-jdbc-ad", admin, "GLUE", "50ml", "WH-01");
        new FactoryItemService(items, unit, guard).recordPolicy(
                "f06-jdbc-po", admin, adopted.factoryItemId(), 1, null, ConsumptionTiming.ON_ISSUE, null);
        ConsumableIssueService service = new ConsumableIssueService(items, stocks, issues, costs, unit, guard);
        CompanyMaterialIssue issued = service.issue(
                "f06-jdbc-ly", admin, adopted.factoryItemId(), adopted.receiptId(), "WS-01", new BigDecimal("2"));
        service.issue("f06-jdbc-ly", admin, adopted.factoryItemId(), adopted.receiptId(), "WS-01", new BigDecimal("2"));
        assertThat(costs.events(issued.issueId())).hasSize(1);
        assertThat(costs.events(issued.issueId()).get(0).amount()).isEqualByComparingTo("6.00");
        assertThat(finance.listPayables()).hasSize(1);
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql",
                    "V99__m3_company_material_issue.sql", "V100__m3_company_material_consumption.sql",
                    "V101__m3_consumable_adoption.sql", "V102__m3_consumable_cost.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    String trimmed = item.trim();
                    if (trimmed.regionMatches(true, 0, "create", 0, 6) || trimmed.regionMatches(true, 0, "alter", 0, 5)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
