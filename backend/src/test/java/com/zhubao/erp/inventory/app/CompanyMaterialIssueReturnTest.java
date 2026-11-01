package com.zhubao.erp.inventory.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.finance.infra.InMemoryFinanceRepository;
import com.zhubao.erp.finance.infra.JdbcFinanceRepository;
import com.zhubao.erp.inventory.domain.CompanyMaterialIssue;
import com.zhubao.erp.inventory.domain.CompanyMaterialReturn;
import com.zhubao.erp.inventory.domain.CompanyMaterialSourceKind;
import com.zhubao.erp.inventory.domain.FactoryItem;
import com.zhubao.erp.inventory.domain.FactoryItemCategory;
import com.zhubao.erp.inventory.domain.OwnerType;
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

class CompanyMaterialIssueReturnTest {

    private final OperatorContext admin = new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 领用退回仍是公司实物且不超过可退() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        InMemoryFinanceRepository finance = new InMemoryFinanceRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f03-item-08", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        CompanyMaterialReceiptService receipts = new CompanyMaterialReceiptService(items, stocks, finance, unit, guard);
        var received = receipts.receive(
                "f03-recv-01", admin, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        CompanyMaterialIssueService service = new CompanyMaterialIssueService(items, stocks, issues, unit, guard);

        CompanyMaterialIssue issued = service.issue(
                "f03-issue01", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("40"), null, "颗", "MD-20261030-0001");
        assertThat(service.owner()).isEqualTo(OwnerType.COMPANY);
        assertThat(issued.batchCode()).isEqualTo(received.batchCode());
        assertThat(issued.targetCode()).isEqualTo("MD-20261030-0001");
        assertThat(issued.returnedQuantity()).isEqualByComparingTo("0");
        assertThat(issued.weight().value()).isNull();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("60");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().weight().value()).isNull();
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("40");
        assertThat(finance.listPayables()).isEmpty();
        assertThat(finance.listReceivables()).isEmpty();

        CompanyMaterialIssue replay = service.issue(
                "f03-issue01", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("40"), null, "颗", "MD-20261030-0001");
        assertThat(replay.issueId()).isEqualTo(issued.issueId());
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("60");

        CompanyMaterialReturn returned = service.returnIssued(
                "f03-back-01", admin, issued.issueId(), 1, new BigDecimal("15"), null, "颗");
        assertThat(returned.issueId()).isEqualTo(issued.issueId());
        assertThat(returned.unit()).isEqualTo("颗");
        assertThat(returned.weight().value()).isNull();
        assertThat(issues.find(issued.issueId()).orElseThrow().returnedQuantity()).isEqualByComparingTo("15");
        assertThat(issues.find(issued.issueId()).orElseThrow().batchCode()).isEqualTo(received.batchCode());
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("75");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("25");
        CompanyMaterialReturn again = service.returnIssued(
                "f03-back-01", admin, issued.issueId(), 1, new BigDecimal("15"), null, "颗");
        assertThat(again.returnId()).isEqualTo(returned.returnId());
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("75");

        assertThatThrownBy(() -> service.returnIssued(
                "f03-back-02", admin, issued.issueId(), 2, new BigDecimal("30"), null, "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退回数量不能超过可退");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("25");
        assertThatThrownBy(() -> service.returnIssued(
                "f03-back-03", admin, issued.issueId(), 2, new BigDecimal("1"), null, "克"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退回单位必须与原领用一致");
        assertThatThrownBy(() -> service.returnIssued(
                "f03-back-04", admin, issued.issueId(), 1, new BigDecimal("1"), null, "颗"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("领用单已被其他人修改，请刷新后重试");

        var weighed = receipts.receive(
                "f03-recv-02", admin, bead.itemId(), new BigDecimal("10"), new BigDecimal("5.000"), "颗", "WH-02", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        CompanyMaterialIssue split = service.issue(
                "f03-issue02", admin, bead.itemId(), weighed.batchCode(), "WH-02", "WS-02",
                new BigDecimal("4"), new BigDecimal("2.000"), "颗", "");
        assertThat(split.weight().value()).isEqualByComparingTo("2.000");
        assertThat(stocks.balance(bead.itemId(), weighed.batchCode(), "WH-02").orElseThrow().quantity()).isEqualByComparingTo("6");
        assertThat(stocks.balance(bead.itemId(), weighed.batchCode(), "WH-02").orElseThrow().weight().value()).isEqualByComparingTo("3.000");
        assertThat(stocks.balance(bead.itemId(), weighed.batchCode(), "WS-02").orElseThrow().weight().value()).isEqualByComparingTo("2.000");

        assertThatThrownBy(() -> service.issue(
                "f03-deny-01", new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL),
                bead.itemId(), received.batchCode(), "WH-01", "WS-09", new BigDecimal("1"), null, "颗", ""))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权");
        assertThatThrownBy(() -> service.issue(
                "f03-zero-wt", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-09",
                new BigDecimal("1"), BigDecimal.ZERO, "颗", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知重量不能写成 0");
    }

    @Test
    void 失败不留下领用() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f03-fail-it", admin, FactoryItemCategory.ACCESSORY, "扣针", "小号", "个", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard).receive(
                "f03-fail-rc", admin, bead.itemId(), new BigDecimal("8"), null, "个", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialIssueService failing = new CompanyMaterialIssueService(
                items, stocks, new InMemoryCompanyMaterialIssueRepository(),
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.issue(
                "f03-fail-01", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("3"), null, "个", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("8");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01")).isEmpty();
    }

    @Test
    void jdbc往返后再退回不超可退() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcFactoryItemRepository items = new JdbcFactoryItemRepository(dataSource);
        JdbcCompanyMaterialReceiptRepository stocks = new JdbcCompanyMaterialReceiptRepository(dataSource);
        JdbcCompanyMaterialIssueRepository issues = new JdbcCompanyMaterialIssueRepository(dataSource);
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f03-jdbc-it", admin, FactoryItemCategory.BEAD, "南红", "8mm", "颗", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, new JdbcFinanceRepository(dataSource), unit, guard).receive(
                "f03-jdbc-rc", admin, bead.itemId(), new BigDecimal("100"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.PURCHASE, "甲供应商", null, null);
        CompanyMaterialIssueService service = new CompanyMaterialIssueService(items, stocks, issues, unit, guard);
        CompanyMaterialIssue issued = service.issue(
                "f03-jdbc-ly", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("40"), null, "颗", "");
        service.returnIssued("f03-jdbc-th", admin, issued.issueId(), 1, new BigDecimal("15"), null, "颗");
        assertThat(issues.find(issued.issueId()).orElseThrow().returnedQuantity()).isEqualByComparingTo("15");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("75");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().weight().state())
                .isEqualTo(StockMeasurement.WeightState.UNKNOWN);
        CompanyMaterialIssueService failing = new CompanyMaterialIssueService(items, stocks, issues,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.issue(
                "f03-jdbc-x1", admin, bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("10"), null, "颗", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("75");
    }

    @Test
    void 接口按原领用退回() {
        InMemoryFactoryItemRepository items = new InMemoryFactoryItemRepository();
        InMemoryCompanyMaterialReceiptRepository stocks = new InMemoryCompanyMaterialReceiptRepository();
        InMemoryCompanyMaterialIssueRepository issues = new InMemoryCompanyMaterialIssueRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        FactoryItem bead = new FactoryItemService(items, unit, guard).register(
                "f03-http-it", admin, FactoryItemCategory.BEAD, "南红", "10mm", "颗", null, null);
        var received = new CompanyMaterialReceiptService(items, stocks, new InMemoryFinanceRepository(), unit, guard).receive(
                "f03-http-rc", admin, bead.itemId(), new BigDecimal("12"), null, "颗", "WH-01", "2026-10-30",
                CompanyMaterialSourceKind.OPENING, "", null, null);
        CompanyMaterialIssueService service = new CompanyMaterialIssueService(items, stocks, issues, unit, guard);
        OperatorContextResolver actors = mock(OperatorContextResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(actors.resolve(request)).thenReturn(admin);
        CompanyMaterialIssueController controller = new CompanyMaterialIssueController(
                service, new CompanyMaterialConsumptionService(stocks, issues, unit, guard), actors);
        CompanyMaterialIssue issued = controller.issue(new CompanyMaterialIssueController.IssueRequest(
                "f03-http-ly", bead.itemId(), received.batchCode(), "WH-01", "WS-01",
                new BigDecimal("5"), null, "颗", "MD-1"), request).data();
        CompanyMaterialReturn returned = controller.returnIssued(issued.issueId(), new CompanyMaterialIssueController.ReturnRequest(
                "f03-http-th", 1, new BigDecimal("2"), null, "颗"), request).data();
        assertThat(returned.issueId()).isEqualTo(issued.issueId());
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WH-01").orElseThrow().quantity()).isEqualByComparingTo("9");
        assertThat(stocks.balance(bead.itemId(), received.batchCode(), "WS-01").orElseThrow().quantity()).isEqualByComparingTo("3");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V36__m3_finance_operations.sql",
                    "V97__m3_factory_item.sql", "V98__m3_company_material_receipt.sql", "V137__m3_company_receipt_dates.sql", "V99__m3_company_material_issue.sql",
                    "V100__m3_company_material_consumption.sql"}) {
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
