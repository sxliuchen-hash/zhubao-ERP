package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.app.FinanceCostService;
import com.zhubao.erp.finance.domain.FinanceCommands.IssueConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.PurchaseConsumableCommand;
import com.zhubao.erp.finance.domain.FinanceCommands.SaveConsumableItemCommand;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.inventory.domain.ConsumableAdoptionStatus;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryConsumableAdoptionRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcConsumableAdoptionRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.inventory.web.ConsumableAdoptionController;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.security.app.OperatorContextResolver;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsumableOpeningAdoptionTest {

    private final OperatorContext admin = new OperatorContext("财务", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 结存只接续一次且来源不清不另开余额() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository receipts = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryConsumableAdoptionRepository adoptions = new InMemoryConsumableAdoptionRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ConsumableOpeningAdoptionService service = new ConsumableOpeningAdoptionService(
                finance, items, receipts, adoptions, unit, guard);
        FinanceCostService costs = new FinanceCostService(finance, service);
        costs.saveItem(new SaveConsumableItemCommand("GLUE", "胶水", "瓶"));
        costs.purchase(new PurchaseConsumableCommand("GLUE", new BigDecimal("10"), new BigDecimal("30.00"), "甲供应商", "2026-10"), "财务");
        costs.issue(new IssueConsumableCommand("GLUE", new BigDecimal("4"), null, "2026-10"), "财务");
        assertThat(finance.listPayables()).hasSize(1);
        assertThat(finance.listConsumableMovements()).hasSize(2);

        var waiting = service.adopt("f05-wait-01", admin, "GLUE", "", "WH-01");
        assertThat(waiting.status()).isEqualTo(ConsumableAdoptionStatus.PENDING);
        assertThat(waiting.reason()).isEqualTo("缺少可独立领用的规格");
        assertThat(waiting.quantity()).isNull();
        assertThat(receipts.balance(waiting.factoryItemId(), "AD-NONE", "WH-01")).isEmpty();

        var adopted = service.adopt("f05-adopt1", admin, "GLUE", "50ml", "WH-01");
        assertThat(adopted.status()).isEqualTo(ConsumableAdoptionStatus.ADOPTED);
        assertThat(adopted.quantity()).isEqualByComparingTo("6");
        assertThat(adopted.amount()).isEqualByComparingTo("18.00");
        assertThat(adopted.receiptId()).isNotBlank();
        var balance = receipts.balance(adopted.factoryItemId(),
                receipts.find(adopted.receiptId()).orElseThrow().batchCode(), "WH-01").orElseThrow();
        assertThat(balance.quantity()).isEqualByComparingTo("6");
        assertThat(balance.weight().value()).isNull();
        assertThat(finance.listPayables()).hasSize(1);
        assertThat(finance.listConsumableMovements()).hasSize(2);
        assertThat(finance.listReceivables()).isEmpty();

        var replay = service.adopt("f05-adopt1", admin, "GLUE", "50ml", "WH-01");
        var again = service.adopt("f05-again1", admin, "GLUE", "50ml", "WH-01");
        assertThat(replay.adoptionId()).isEqualTo(adopted.adoptionId());
        assertThat(again.adoptionId()).isEqualTo(adopted.adoptionId());
        assertThat(receipts.balance(adopted.factoryItemId(),
                receipts.find(adopted.receiptId()).orElseThrow().batchCode(), "WH-01").orElseThrow().quantity())
                .isEqualByComparingTo("6");
        assertThat(finance.listPayables()).hasSize(1);
        assertThatThrownBy(() -> costs.purchase(new PurchaseConsumableCommand(
                "GLUE", new BigDecimal("1"), new BigDecimal("3.00"), "甲供应商", "2026-10"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该耗材数量已接续，不能再写旧数量账");
        assertThatThrownBy(() -> costs.issue(new IssueConsumableCommand("GLUE", new BigDecimal("1"), null, "2026-10"), "财务"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该耗材数量已接续，不能再写旧数量账");

        costs.saveItem(new SaveConsumableItemCommand("EMPTY", "空瓶", "个"));
        var unclear = service.adopt("f05-empty1", admin, "EMPTY", "默认", "WH-01");
        assertThat(unclear.status()).isEqualTo(ConsumableAdoptionStatus.PENDING);
        assertThat(unclear.reason()).isEqualTo("没有可核对的数量来源");
        assertThat(items.findIdentity(FactoryItemCategory.CONSUMABLE, "空瓶", "默认", "个")).isEmpty();
        costs.purchase(new PurchaseConsumableCommand("EMPTY", new BigDecimal("1"), new BigDecimal("2.00"), "乙", "2026-10"), "财务");
        assertThat(finance.listPayables()).hasSize(2);
    }

    @Test
    void 失败不留下接续余额() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository receipts = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryConsumableAdoptionRepository adoptions = new InMemoryConsumableAdoptionRepository();
        FinanceCostService costs = new FinanceCostService(finance);
        costs.saveItem(new SaveConsumableItemCommand("PAPER", "砂纸", "张"));
        costs.purchase(new PurchaseConsumableCommand("PAPER", new BigDecimal("5"), new BigDecimal("8.00"), "甲", "2026-10"), "财务");
        ConsumableOpeningAdoptionService failing = new ConsumableOpeningAdoptionService(
                finance, items, receipts, adoptions,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.adopt("f05-fail-01", admin, "PAPER", "240目", "WH-01"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(adoptions.findByOldCode("PAPER")).isEmpty();
        assertThat(items.findIdentity(FactoryItemCategory.CONSUMABLE, "砂纸", "240目", "张")).isEmpty();
        assertThat(finance.listPayables()).hasSize(1);
    }

    @Test
    void jdbc接续往返且不重复期初() throws Exception {
        JdbcDataSource dataSource = dataSource();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository receipts = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcConsumableAdoptionRepository adoptions = new JdbcConsumableAdoptionRepository(dataSource);
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        ConsumableOpeningAdoptionService service = new ConsumableOpeningAdoptionService(
                finance, items, receipts, adoptions, unit, guard);
        FinanceCostService costs = new FinanceCostService(finance, service);
        costs.saveItem(new SaveConsumableItemCommand("GLUE", "胶水", "瓶"));
        costs.purchase(new PurchaseConsumableCommand("GLUE", new BigDecimal("10"), new BigDecimal("30.00"), "甲", "2026-10"), "财务");
        var adopted = service.adopt("f05-jdbc-01", admin, "GLUE", "50ml", "WH-01");
        var again = service.adopt("f05-jdbc-02", admin, "GLUE", "50ml", "WH-01");
        assertThat(again.adoptionId()).isEqualTo(adopted.adoptionId());
        assertThat(adoptions.findByOldCode("GLUE").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(receipts.balance(adopted.factoryItemId(), receipts.find(adopted.receiptId()).orElseThrow().batchCode(), "WH-01")
                .orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(finance.listPayables()).hasSize(1);
    }

    @Test
    void 接口把缺少来源列为待核() {
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        ConsumableOpeningAdoptionService service = new ConsumableOpeningAdoptionService(
                finance, new InMemoryFactoryItemRepository(), new InMemoryCompanyMaterialReceiptRepository(),
                new InMemoryConsumableAdoptionRepository(), unit, guard);
        new FinanceCostService(finance).saveItem(new SaveConsumableItemCommand("BAG", "包装袋", "包"));
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(admin);
        var result = new ConsumableAdoptionController(service, actors).adopt(
                new ConsumableAdoptionController.AdoptRequest("f05-http-01", "BAG", "小号", "WH-01"), request).data();
        assertThat(result.status()).isEqualTo(ConsumableAdoptionStatus.PENDING);
        assertThat(result.quantity()).isNull();
        assertThat(result.amount()).isNull();
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V97__m3_factory_item.sql",
                    "V98__m3_company_material_receipt.sql", "V137__m3_company_receipt_dates.sql", "V101__m3_consumable_adoption.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6) || item.trim().regionMatches(true, 0, "alter", 0, 5)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
