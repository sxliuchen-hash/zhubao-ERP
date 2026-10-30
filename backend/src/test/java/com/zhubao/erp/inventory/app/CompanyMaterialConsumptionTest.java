package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialAmountState;
import com.zhubao.erp.inventory.domain.CompanyMaterialConsumption;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.InMemoryCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.InMemoryFactoryItemRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialIssueRepository;
import com.zhubao.erp.inventory.infra.JdbcCompanyMaterialReceiptRepository;
import com.zhubao.erp.inventory.infra.JdbcFactoryItemRepository;
import com.zhubao.erp.inventory.web.CompanyMaterialIssueController;
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

class CompanyMaterialConsumptionTest {

    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 一百分成七十耗用二十退回十留下且未知金额不写成零() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f04-item-01", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard).receive(
                "f04-recv-01", admin, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        CompanyMaterialIssueService issuing = new CompanyMaterialIssueService(items, stocks, issues, unit, guard);
        CompanyMaterialIssue issued = issuing.issue(
                "f04-issue01", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("100"), null, "颗", "MD-20261030-0001");
        CompanyMaterialConsumptionService service = new CompanyMaterialConsumptionService(stocks, issues, unit, guard);

        CompanyMaterialConsumption closed = service.confirm(
                "f04-use-001", admin, issued.issueId(), 1,
                new BigDecimal("70"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("10"),
                null, "");
        assertThat(closed.consumedQuantity()).isEqualByComparingTo("70");
        assertThat(closed.returnedQuantity()).isEqualByComparingTo("20");
        assertThat(closed.keptQuantity()).isEqualByComparingTo("10");
        assertThat(closed.scrapQuantity()).isEqualByComparingTo("10");
        assertThat(closed.consumedQuantity().add(closed.returnedQuantity()).add(closed.keptQuantity())).isEqualByComparingTo("100");
        assertThat(closed.amountState()).isEqualTo(CompanyMaterialAmountState.INCOMPLETE);
        assertThat(closed.amount()).isNull();
        assertThat(closed.targetCode()).isEqualTo("MD-20261030-0001");
        assertThat(issues.find(issued.issueId()).orElseThrow().consumedQuantity()).isEqualByComparingTo("70");
        assertThat(issues.find(issued.issueId()).orElseThrow().returnedQuantity()).isEqualByComparingTo("20");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().weight().state())
                .isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("20");
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(finance.listPayables()).isEmpty();

        CompanyMaterialConsumption replay = service.confirm(
                "f04-use-001", admin, issued.issueId(), 1,
                new BigDecimal("70"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("10"),
                null, "");
        assertThat(replay.consumptionId()).isEqualTo(closed.consumptionId());
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(finance.listReceivables()).isEmpty();

        assertThatThrownBy(() -> service.confirm(
                "f04-use-bad", admin, issued.issueId(), 2,
                new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("5"), BigDecimal.ZERO, null, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("耗用、退回和留下必须等于尚未结清的领用");
        assertThatThrownBy(() -> service.confirm(
                "f04-zero-01", admin, issued.issueId(), 2,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知金额不能写成 0");

        CompanyMaterialIssue pricedIssue = issuing.issue(
                "f04-issue02", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-02",
                new BigDecimal("20"), null, "颗", "MD-20261030-0001");
        CompanyMaterialConsumption priced = service.confirm(
                "f04-use-002", admin, pricedIssue.issueId(), 1,
                new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("12.50"), "MD-20261030-0001");
        assertThat(priced.amountState()).isEqualTo(CompanyMaterialAmountState.KNOWN);
        assertThat(priced.amount()).isEqualByComparingTo("12.50");
        assertThat(finance.listReceivables()).isEmpty();
        assertThat(finance.listPayables()).isEmpty();

        assertThatThrownBy(() -> service.confirm(
                "f04-deny-01", new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL), issued.issueId(), 2,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, ""))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void 失败不留下耗用() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f04-fail-it", admin, FactoryItemCategory.ACCESSORY, "扣针", "小号", "个", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard).receive(
                "f04-fail-rc", admin, bead.itemId(), new BigDecimal("100"), null, "个", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialIssue issued = new CompanyMaterialIssueService(items, stocks, issues, unit, guard).issue(
                "f04-fail-ly", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("100"), null, "个", "");
        CompanyMaterialConsumptionService failing = new CompanyMaterialConsumptionService(
                stocks, issues, new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
            throw new IllegalStateException("失败");
        }), guard);
        assertThatThrownBy(() -> failing.confirm(
                "f04-fail-01", admin, issued.issueId(), 1,
                new BigDecimal("70"), new BigDecimal("20"), new BigDecimal("10"), BigDecimal.ZERO, null, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("100");
        assertThat(issues.find(issued.issueId()).orElseThrow().consumedQuantity()).isEqualByComparingTo("0");
        assertThat(issues.findConsumption("HY-NONE")).isEmpty();
    }

    @Test
    void jdbc确认后重放不二次计成本() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcCompanyMaterialIssueRepository issues = new JdbcCompanyMaterialIssueRepository(dataSource);
        JdbcFinanceRepository finance = new JdbcFinanceRepository(dataSource);
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f04-jdbc-it", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard).receive(
                "f04-jdbc-rc", admin, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        CompanyMaterialIssue issued = new CompanyMaterialIssueService(items, stocks, issues, unit, guard).issue(
                "f04-jdbc-ly", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("100"), null, "颗", "MD-1");
        CompanyMaterialConsumptionService service = new CompanyMaterialConsumptionService(stocks, issues, unit, guard);
        CompanyMaterialConsumption closed = service.confirm(
                "f04-jdbc-01", admin, issued.issueId(), 1,
                new BigDecimal("70"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("10"), null, "");
        CompanyMaterialConsumption again = service.confirm(
                "f04-jdbc-01", admin, issued.issueId(), 1,
                new BigDecimal("70"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("10"), null, "");
        assertThat(again.consumptionId()).isEqualTo(closed.consumptionId());
        assertThat(again.amount()).isNull();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("10");
        assertThat(finance.listReceivables()).isEmpty();
    }

    @Test
    void 接口不把耗用费用写成客户应收() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f04-http-it", admin, FactoryItemCategory.ACCESSORY, "镶标", "小号", "个", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard).receive(
                "f04-http-rc", admin, bead.itemId(), new BigDecimal("30"), null, "个", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialIssue issued = new CompanyMaterialIssueService(items, stocks, issues, unit, guard).issue(
                "f04-http-ly", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("30"), null, "个", "MD-9");
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(admin);
        CompanyMaterialIssueController controller = new CompanyMaterialIssueController(
                new CompanyMaterialIssueService(items, stocks, issues, unit, guard),
                new CompanyMaterialConsumptionService(stocks, issues, unit, guard), actors);
        CompanyMaterialConsumption closed = controller.confirm(issued.issueId(), new CompanyMaterialIssueController.ConsumptionRequest(
                "f04-http-01", 1, new BigDecimal("30"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, "MD-9"), request).data();
        assertThat(closed.amount()).isNull();
        assertThat(finance.listReceivables()).isEmpty();
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V36__m3_finance_operations.sql",
                    "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql",
                    "V99__m3_company_material_issue.sql", "V100__m3_company_material_consumption.sql"}) {
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
